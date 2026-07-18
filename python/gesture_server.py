import asyncio
import base64
import json
import logging
import os
import sys
import time
import urllib.request
from pathlib import Path

import cv2
import mediapipe as mp
import websockets
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision


SERVER_URL = "ws://127.0.0.1:8765"
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
SEND_FPS = 60
IMAGE_STREAM_FPS = 12
RECONNECT_DELAY_SECONDS = 0.25
LOCAL_COMPAT_MODE = True
# 默认关闭 Python 本地预览窗，只保留 Java 主界面中的实时画面，避免双窗口混乱。
# 如需单独调试 MediaPipe 识别窗，可在启动前设置:
#   $env:GESTURE_SERVER_SHOW_PREVIEW = "1"
SHOW_PREVIEW = os.getenv("GESTURE_SERVER_SHOW_PREVIEW", "0") == "1"
SEND_IMAGE_STREAM = True
IMAGE_JPEG_QUALITY = 30
PRINT_PAYLOAD_SAMPLE = True
PAYLOAD_LOG_INTERVAL_SECONDS = 1.0
MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/latest/hand_landmarker.task"
)
MODEL_FILE_NAME = "hand_landmarker.task"


logging.basicConfig(level=logging.INFO, format="[Python] %(message)s")
LOGGER = logging.getLogger("gesture_server")

MP_IMAGE = mp.Image
MP_IMAGE_FORMAT = mp.ImageFormat
BASE_OPTIONS = mp_python.BaseOptions
HAND_LANDMARKER = mp_vision.HandLandmarker
HAND_LANDMARKER_OPTIONS = mp_vision.HandLandmarkerOptions
RUNNING_MODE = mp_vision.RunningMode
MP_DRAW = mp_vision.drawing_utils
MP_STYLES = mp_vision.drawing_styles
HAND_CONNECTIONS = mp_vision.HandLandmarksConnections.HAND_CONNECTIONS


def get_app_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def get_runtime_data_dir() -> Path:
    if getattr(sys, "frozen", False):
        local_appdata = Path(os.getenv("LOCALAPPDATA", get_app_dir()))
        return local_appdata / "gesture-game-hall" / "gesture_server"
    return get_app_dir()


def get_bundled_model_candidates() -> list[Path]:
    candidates = [get_app_dir() / "models" / MODEL_FILE_NAME]
    if getattr(sys, "_MEIPASS", None):
        candidates.append(Path(sys._MEIPASS) / "models" / MODEL_FILE_NAME)
    return candidates


class GestureState:
    def __init__(self):
        self.prev_hand_x = 0.0
        self.prev_hand_y = 0.0
        self.previous_detected = False
        self.last_payload_log_time = 0.0
        self.last_image_send_time = 0.0
        self.last_raw_gesture = "none"

    def build_payload(self, hands) -> dict:
        if not hands:
            self.previous_detected = False
            self.last_raw_gesture = "none"
            return {
                "handX": 0.0,
                "handY": 0.0,
                "prevHandX": 0.0,
                "prevHandY": 0.0,
                "velocityX": 0.0,
                "velocityY": 0.0,
                "gesture": "none",
                "confidence": 0.0,
                "handDetected": False,
                "handCount": 0,
                "navigationPalm": False,
                "secondHandX": 0.0,
                "secondHandY": 0.0,
                "twoHandSpread": 0.0,
                "bothHandsOpen": False,
            }

        hand_landmarks, handedness_label = hands[0]
        hand_x, hand_y = calculate_hand_center(hand_landmarks)
        if self.previous_detected:
            velocity_x = clamp(hand_x - self.prev_hand_x)
            velocity_y = clamp(hand_y - self.prev_hand_y)
            prev_hand_x = self.prev_hand_x
            prev_hand_y = self.prev_hand_y
        else:
            velocity_x = 0.0
            velocity_y = 0.0
            prev_hand_x = hand_x
            prev_hand_y = hand_y

        raw_gesture, gesture, confidence = classify_gesture(hand_landmarks, handedness_label)
        navigation_palm = is_navigation_palm(hand_landmarks)
        pointing_direction = classify_pointing_direction(hand_landmarks) if gesture == "pointing" else "none"
        self.last_raw_gesture = raw_gesture

        self.prev_hand_x = hand_x
        self.prev_hand_y = hand_y
        self.previous_detected = True

        second_hand_x = 0.0
        second_hand_y = 0.0
        two_hand_spread = 0.0
        both_hands_open = False
        if len(hands) >= 2:
            second_landmarks, second_handedness = hands[1]
            second_hand_x, second_hand_y = calculate_hand_center(second_landmarks)
            _, second_gesture, _ = classify_gesture(second_landmarks, second_handedness)
            center_distance = ((hand_x - second_hand_x) ** 2 + (hand_y - second_hand_y) ** 2) ** 0.5
            two_hand_spread = max(0.0, min(1.0, (center_distance - 0.15) / 0.50))
            both_hands_open = gesture == "open" and second_gesture == "open"

        return {
            "handX": round(hand_x, 4),
            "handY": round(hand_y, 4),
            "prevHandX": round(prev_hand_x, 4),
            "prevHandY": round(prev_hand_y, 4),
            "velocityX": round(velocity_x, 4),
            "velocityY": round(velocity_y, 4),
            "gesture": gesture,
            # 可选兼容字段：Java 旧版本会忽略；新版仅用它确认“食指朝左上”退出。
            "pointingDirection": pointing_direction,
            "confidence": round(confidence, 4),
            "handDetected": True,
            "handCount": len(hands),
            "navigationPalm": navigation_palm,
            "secondHandX": round(second_hand_x, 4),
            "secondHandY": round(second_hand_y, 4),
            "twoHandSpread": round(two_hand_spread, 4),
            "bothHandsOpen": both_hands_open,
        }


def clamp(value: float) -> float:
    if value < -1.0:
        return -1.0
    if value > 1.0:
        return 1.0
    return value


def calculate_hand_center(hand_landmarks):
    points = hand_landmarks
    sample_indices = [0, 5, 9, 13, 17]
    center_x = sum(points[index].x for index in sample_indices) / len(sample_indices)
    center_y = sum(points[index].y for index in sample_indices) / len(sample_indices)
    return center_x, center_y


def joint_angle(point_a, vertex, point_c) -> float:
    """Return the smaller angle ABC in degrees, independent of hand rotation."""
    ab_x, ab_y = point_a.x - vertex.x, point_a.y - vertex.y
    cb_x, cb_y = point_c.x - vertex.x, point_c.y - vertex.y
    denominator = max(1e-9, (ab_x * ab_x + ab_y * ab_y) ** 0.5
                      * (cb_x * cb_x + cb_y * cb_y) ** 0.5)
    cosine = max(-1.0, min(1.0, (ab_x * cb_x + ab_y * cb_y) / denominator))
    import math
    return math.degrees(math.acos(cosine))


def is_finger_extended(points, tip_index: int, pip_index: int) -> bool:
    """Rotation-independent extension test using PIP angle and wrist distance."""
    mcp_index = pip_index - 1
    angle_threshold = 118.0 if LOCAL_COMPAT_MODE else 138.0
    distance_ratio = 1.005 if LOCAL_COMPAT_MODE else 1.02
    straight = joint_angle(points[mcp_index], points[pip_index], points[tip_index]) >= angle_threshold
    tip_from_wrist = landmark_distance(points[tip_index], points[0])
    pip_from_wrist = landmark_distance(points[pip_index], points[0])
    return straight and tip_from_wrist > pip_from_wrist * distance_ratio


def is_navigation_palm(points) -> bool:
    """Loose open-palm test used only for lobby navigation."""
    extended = sum(is_finger_extended(points, tip, pip)
                   for tip, pip in ((8, 6), (12, 10), (16, 14), (20, 18)))
    if LOCAL_COMPAT_MODE and extended < 3:
        # 笔记本内置摄像头下，用更宽松的“指尖高于第二关节”兜底大厅导航张手。
        loose_extended = sum(
            1 for tip, pip in ((8, 6), (12, 10), (16, 14), (20, 18))
            if points[tip].y < points[pip].y
        )
        extended = max(extended, loose_extended)
    return extended >= 3


def is_thumb_extended(points, handedness_label: str) -> bool:
    """旋转无关的拇指伸开检测：关节角度 + 指尖比指根远离手腕。"""
    straight = joint_angle(points[2], points[3], points[4]) >= 135.0
    tip_from_wrist = landmark_distance(points[4], points[0])
    ip_from_wrist = landmark_distance(points[3], points[0])
    return straight and tip_from_wrist > ip_from_wrist * 1.02


def landmark_distance(point_a, point_b) -> float:
    dx = point_a.x - point_b.x
    dy = point_a.y - point_b.y
    return (dx * dx + dy * dy) ** 0.5


def classify_pointing_direction(points) -> str:
    """Classify the actual index-finger direction, not the hand's screen position."""
    dx = points[8].x - points[5].x
    dy = points[8].y - points[5].y
    length = max(1e-9, (dx * dx + dy * dy) ** 0.5)
    nx, ny = dx / length, dy / length
    # Image coordinates: x grows right and y grows down. A diagonal sector is
    # intentionally used so ordinary upward/side pointing in a game cannot exit.
    if nx < -0.35 and ny < -0.35:
        return "up_left"
    return "other"


def is_thumbs_up(points, thumb_extended: bool, index_extended: bool, middle_extended: bool,
                 ring_extended: bool, pinky_extended: bool) -> bool:
    other_fingers_closed = not index_extended and not middle_extended and not ring_extended and not pinky_extended
    thumb_tip = points[4]
    thumb_mcp = points[2]
    wrist = points[0]
    return (
        thumb_extended
        and other_fingers_closed
        and thumb_tip.y < thumb_mcp.y
        and thumb_tip.y < wrist.y
        and abs(thumb_tip.x - thumb_mcp.x) < 0.2
    )


def is_thumbs_down(points, thumb_extended: bool, index_extended: bool, middle_extended: bool,
                   ring_extended: bool, pinky_extended: bool) -> bool:
    other_fingers_closed = not index_extended and not middle_extended and not ring_extended and not pinky_extended
    thumb_tip = points[4]
    thumb_mcp = points[2]
    wrist = points[0]
    return (
        thumb_extended
        and other_fingers_closed
        and thumb_tip.y > thumb_mcp.y
        and thumb_tip.y > wrist.y
        and abs(thumb_tip.x - thumb_mcp.x) < 0.2
    )


def is_ok_sign(points, middle_extended: bool, ring_extended: bool, pinky_extended: bool) -> bool:
    palm_span = landmark_distance(points[5], points[17])
    thumb_index_distance = landmark_distance(points[4], points[8])
    return (
        thumb_index_distance < max(0.06, palm_span * 0.35)
        and middle_extended
        and ring_extended
        and pinky_extended
    )


def is_nine_sign(points, middle_extended: bool, ring_extended: bool, pinky_extended: bool) -> bool:
    palm_span = landmark_distance(points[5], points[17])
    thumb_index_distance = landmark_distance(points[4], points[8])
    return (
        thumb_index_distance < max(0.06, palm_span * 0.35)
        and not middle_extended
        and not ring_extended
        and not pinky_extended
    )


def classify_gesture(hand_landmarks, handedness_label: str):
    points = hand_landmarks

    thumb_extended = is_thumb_extended(points, handedness_label)
    index_extended = is_finger_extended(points, 8, 6)
    middle_extended = is_finger_extended(points, 12, 10)
    ring_extended = is_finger_extended(points, 16, 14)
    pinky_extended = is_finger_extended(points, 20, 18)

    extended_count = sum(
        [thumb_extended, index_extended, middle_extended, ring_extended, pinky_extended]
    )

    # PEACE 预处理：角度兜底放 fist 之前，防止背朝摄像头时误判为握拳
    if not ring_extended and not pinky_extended:
        idx_angle = joint_angle(points[5], points[6], points[8])
        mid_angle = joint_angle(points[9], points[10], points[12])
        if idx_angle >= 128.0 and mid_angle >= 128.0:
            return "two", "peace", 0.90

    # A fist is defined by the four non-thumb fingers being curled. Do this before
    # thumb variants: a side-on fist often makes the thumb look horizontally
    # extended and used to be misclassified as another gesture.
    curled_count = sum([not index_extended, not middle_extended,
                        not ring_extended, not pinky_extended])
    palm_span = landmark_distance(points[5], points[17])
    fingertips_near_palm = sum(
        landmark_distance(points[index], points[9]) < palm_span * 1.35
        for index in (8, 12, 16, 20)
    )
    if curled_count >= 3 and fingertips_near_palm >= 3:
        return "fist", "fist", 0.97

    if is_thumbs_up(
        points,
        thumb_extended,
        index_extended,
        middle_extended,
        ring_extended,
        pinky_extended,
    ):
        # 在不修改公共契约的前提下，把点赞兼容映射为 POINTING 传给 Java。
        return "thumbs_up", "pointing", 0.94

    if is_thumbs_down(
        points,
        thumb_extended,
        index_extended,
        middle_extended,
        ring_extended,
        pinky_extended,
    ):
        return "thumbs_down", "none", 0.94

    if is_ok_sign(points, middle_extended, ring_extended, pinky_extended):
        return "zero_ok", "none", 0.93

    if is_nine_sign(points, middle_extended, ring_extended, pinky_extended):
        return "nine", "none", 0.93

    if thumb_extended and not index_extended and not middle_extended and not ring_extended and pinky_extended:
        return "six", "none", 0.92

    if thumb_extended and index_extended and not middle_extended and not ring_extended and not pinky_extended:
        return "gun", "none", 0.92

    if thumb_extended and index_extended and not middle_extended and not ring_extended and pinky_extended:
        return "love_you", "none", 0.92

    if thumb_extended and index_extended and middle_extended and not ring_extended and not pinky_extended:
        return "seven", "none", 0.92

    if thumb_extended and index_extended and middle_extended and ring_extended and not pinky_extended:
        return "eight", "none", 0.92

    if index_extended and not middle_extended and not ring_extended and pinky_extended:
        if not thumb_extended or extended_count <= 3:
            return "rock", "none", 0.91

    if index_extended and middle_extended and ring_extended and pinky_extended and not thumb_extended:
        return "four", "none", 0.93

    if index_extended and middle_extended and ring_extended and not pinky_extended and not thumb_extended:
        return "three", "none", 0.92

    # PEACE：食指+中指伸开，无名指小指蜷
    if index_extended and middle_extended and not ring_extended and not pinky_extended:
        return "two", "peace", 0.95
    # 兜底1：食指中指角度不太够但无名指小指蜷 → 大概率是 PEACE
    if not ring_extended and not pinky_extended:
        idx_d = landmark_distance(points[8], points[0])
        mid_d = landmark_distance(points[12], points[0])
        avg_other = (landmark_distance(points[16], points[0]) + landmark_distance(points[20], points[0])) / 2.0
        if max(idx_d, mid_d) > avg_other * 1.05:
            return "two", "peace", 0.88
    # 兜底2：背朝摄像头时指尖比指根近 → 角度对就行
    if not ring_extended and not pinky_extended:
        idx_angle = joint_angle(points[5], points[6], points[8])
        mid_angle = joint_angle(points[9], points[10], points[12])
        if idx_angle >= 130.0 and mid_angle >= 130.0:
            return "two", "peace", 0.85

    # POINTING：角度判定 + 旋转无关的距离特判
    if index_extended and not ring_extended and not pinky_extended:
        if extended_count <= 3 and (not middle_extended or extended_count == 3):
            return "one", "pointing", 0.94
    # 兜底：食指伸直度不够，但食指指尖明显是离手腕最远的指尖 → 还是指向
    index_dist = landmark_distance(points[8], points[0])
    other_tips_max = max(landmark_distance(points[12], points[0]),
                         landmark_distance(points[16], points[0]),
                         landmark_distance(points[20], points[0]))
    if index_dist > other_tips_max * 1.08 and not ring_extended and not pinky_extended and not middle_extended:
        return "one", "pointing", 0.90

    if extended_count >= 4 and index_extended and middle_extended and ring_extended and pinky_extended:
        return "five", "open", 0.98

    thumb_index_dist = landmark_distance(points[4], points[8])
    if (thumb_index_dist < 0.20
        and not index_extended
        and not middle_extended
        and not ring_extended
        and not pinky_extended):
        return "fist", "fist", 0.97
    # 兜底：拇指位置不算扩展时也能匹配侧握拳等姿势
    if (extended_count <= 1 and not index_extended and not middle_extended
        and not ring_extended and not pinky_extended):
        return "fist", "fist", 0.96

    # 不确定时优先回退到 none，避免 Python 端误发错误基础手势。
    return "none", "none", 0.55


def draw_preview(frame, hand_landmarks, payload, raw_gesture: str):
    if hand_landmarks is not None:
        MP_DRAW.draw_landmarks(
            frame,
            hand_landmarks,
            HAND_CONNECTIONS,
            MP_STYLES.get_default_hand_landmarks_style(),
            MP_STYLES.get_default_hand_connections_style(),
        )

    cv2.putText(
        frame,
        f"raw: {raw_gesture}",
        (16, 32),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (120, 255, 120),
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        f"core: {payload['gesture']}",
        (16, 62),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (80, 200, 255),
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        f"confidence: {payload['confidence']:.2f}",
        (16, 92),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (255, 200, 80),
        2,
        cv2.LINE_AA,
    )


def encode_image_data(frame) -> str:
    small = cv2.resize(frame, (320, 240), interpolation=cv2.INTER_NEAREST)
    ok, encoded = cv2.imencode(
        ".jpg",
        small,
        [int(cv2.IMWRITE_JPEG_QUALITY), IMAGE_JPEG_QUALITY],
    )
    if not ok:
        return ""
    base64_bytes = base64.b64encode(encoded.tobytes()).decode("ascii")
    return f"data:image/jpeg;base64,{base64_bytes}"


def ensure_hand_landmarker_model() -> Path:
    for candidate in get_bundled_model_candidates():
        if candidate.exists():
            return candidate

    model_path = get_runtime_data_dir() / "models" / MODEL_FILE_NAME
    if model_path.exists():
        return model_path

    model_path.parent.mkdir(parents=True, exist_ok=True)
    LOGGER.info("正在下载最新版 MediaPipe Hand Landmarker 模型...")
    urllib.request.urlretrieve(MODEL_URL, model_path)
    LOGGER.info("模型下载完成: %s", model_path)
    return model_path


def create_landmarker():
    model_path = ensure_hand_landmarker_model()
    options = HAND_LANDMARKER_OPTIONS(
        base_options=BASE_OPTIONS(model_asset_path=str(model_path)),
        running_mode=RUNNING_MODE.VIDEO,
        num_hands=2,
        min_hand_detection_confidence=0.28 if LOCAL_COMPAT_MODE else 0.42,
        min_hand_presence_confidence=0.24 if LOCAL_COMPAT_MODE else 0.42,
        min_tracking_confidence=0.30 if LOCAL_COMPAT_MODE else 0.45,
    )
    return HAND_LANDMARKER.create_from_options(options)


def open_camera():
    backend_candidates = [
        ("CAP_DSHOW", cv2.CAP_DSHOW),
        ("CAP_MSMF", cv2.CAP_MSMF),
        ("CAP_ANY", cv2.CAP_ANY),
    ]

    for backend_name, backend in backend_candidates:
        cap = cv2.VideoCapture(CAMERA_INDEX, backend)
        if not cap.isOpened():
            cap.release()
            continue

        cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

        ok, _ = cap.read()
        if ok:
            LOGGER.info("摄像头已打开，后端: %s，索引: %s", backend_name, CAMERA_INDEX)
            return cap

        LOGGER.warning("摄像头后端 %s 可初始化但无法读取画面，尝试下一个后端", backend_name)
        cap.release()

    raise RuntimeError(
        "默认摄像头打开失败。请确认没有被 Java、微信、QQ、浏览器等程序占用，"
        "或把 CAMERA_INDEX 改成 1 再试。"
    )


def extract_hands(result):
    if not result or not result.hand_landmarks:
        return []

    hands = []
    for index, landmarks in enumerate(result.hand_landmarks[:2]):
        handedness_label = "Right"
        if result.handedness and len(result.handedness) > index and result.handedness[index]:
            handedness_label = result.handedness[index][0].category_name or "Right"
        hands.append((landmarks, handedness_label))
    return hands


async def stream_gestures():
    LOGGER.info("WebSocket 服务已启动，等待 Java 连接...")

    cap = open_camera()
    state = GestureState()
    last_timestamp_ms = -1
    
    # 动态控制图像流：仅在非 GAME 状态下发送，避免游戏时卡顿
    java_state = {"current": "LOGIN"}

    try:
        with create_landmarker() as hand_landmarker:
            LOGGER.info("GESTURE_ENGINE_READY: 手势模型已就绪，等待 Java 服务...")
            while True:
                try:
                    async with websockets.connect(SERVER_URL, max_size=None) as websocket:
                        LOGGER.info("已连接到 Java WebSocket 服务: %s", SERVER_URL)

                        async def receive_state():
                            try:
                                async for message in websocket:
                                    data = json.loads(message)
                                    if "state" in data:
                                        java_state["current"] = data["state"]
                                        LOGGER.info(f"收到 Java 状态切换: {java_state['current']}")
                            except Exception as e:
                                pass

                        receive_task = asyncio.create_task(receive_state())

                        while True:
                            loop_start = time.perf_counter()
                            now = time.time()
                            ok, frame = cap.read()
                            if not ok:
                                await asyncio.sleep(0.02)
                                continue

                            # 生成严格递增的时间戳，防止 MediaPipe 在 Windows 下因时钟精度(15ms)抛出异常导致崩溃
                            timestamp_ms = int(time.perf_counter() * 1000)
                            if timestamp_ms <= last_timestamp_ms:
                                timestamp_ms = last_timestamp_ms + 1
                            last_timestamp_ms = timestamp_ms

                            frame = cv2.flip(frame, 1)
                            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                            mp_image = MP_IMAGE(image_format=MP_IMAGE_FORMAT.SRGB, data=rgb)
                            result = hand_landmarker.detect_for_video(
                                mp_image,
                                timestamp_ms,
                            )
                            hands = extract_hands(result)

                            payload = state.build_payload(hands)
                            output_frame = frame
                            
                            # 动态决定是否需要处理/发送图像流
                            is_game_state = java_state["current"] == "GAME"
                            should_send_image = SEND_IMAGE_STREAM and not is_game_state

                            if SHOW_PREVIEW or should_send_image:
                                output_frame = frame.copy()
                                draw_preview(
                                    output_frame,
                                    hands[0][0] if hands else None,
                                    payload,
                                    state.last_raw_gesture,
                                )
                                if len(hands) > 1:
                                    MP_DRAW.draw_landmarks(
                                        output_frame,
                                        hands[1][0],
                                        HAND_CONNECTIONS,
                                        MP_STYLES.get_default_hand_landmarks_style(),
                                        MP_STYLES.get_default_hand_connections_style(),
                                    )

                            if should_send_image:
                                if now - state.last_image_send_time >= 1.0 / IMAGE_STREAM_FPS:
                                    encoded_image = encode_image_data(output_frame)
                                    if encoded_image:
                                        payload["image_data"] = encoded_image
                                    state.last_image_send_time = now

                            if PRINT_PAYLOAD_SAMPLE and now - state.last_payload_log_time >= PAYLOAD_LOG_INTERVAL_SECONDS:
                                payload_log = dict(payload)
                                payload_log["raw_gesture"] = state.last_raw_gesture
                                if "image_data" in payload_log:
                                    payload_log["image_data"] = "<base64_image>"
                                LOGGER.info("当前发送 JSON: %s", json.dumps(payload_log, ensure_ascii=True))
                                state.last_payload_log_time = now
                            await websocket.send(json.dumps(payload, ensure_ascii=True))

                            if SHOW_PREVIEW:
                                cv2.imshow("MediaPipe Gesture Server", output_frame)
                                if cv2.waitKey(1) & 0xFF == 27:
                                    LOGGER.info("检测到 ESC，正在退出...")
                                    return

                            elapsed = time.perf_counter() - loop_start
                            await asyncio.sleep(max(0.0, (1.0 / SEND_FPS) - elapsed))
                except (ConnectionRefusedError, OSError, websockets.WebSocketException) as error:
                    LOGGER.info("Java 端暂未就绪，%.1f 秒后重连: %s", RECONNECT_DELAY_SECONDS, error)
                    await asyncio.sleep(RECONNECT_DELAY_SECONDS)
                finally:
                    if 'receive_task' in locals():
                        receive_task.cancel()
    finally:
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    try:
        asyncio.run(stream_gestures())
    except KeyboardInterrupt:
        LOGGER.info("已手动停止 gesture_server.py")
