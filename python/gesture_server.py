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
SEND_FPS = 30
IMAGE_STREAM_FPS = 20
RECONNECT_DELAY_SECONDS = 1.5
# 默认关闭 Python 本地预览窗，只保留 Java 主界面中的实时画面，避免双窗口混乱。
# 如需单独调试 MediaPipe 识别窗，可在启动前设置:
#   $env:GESTURE_SERVER_SHOW_PREVIEW = "1"
SHOW_PREVIEW = os.getenv("GESTURE_SERVER_SHOW_PREVIEW", "0") == "1"
SEND_IMAGE_STREAM = True
IMAGE_JPEG_QUALITY = 45
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
        self.last_encoded_image = ""
        self.last_raw_gesture = "none"

    def build_payload(self, result) -> dict:
        if result is None:
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
            }

        hand_landmarks, handedness_label = result
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
        self.last_raw_gesture = raw_gesture

        self.prev_hand_x = hand_x
        self.prev_hand_y = hand_y
        self.previous_detected = True

        return {
            "handX": round(hand_x, 4),
            "handY": round(hand_y, 4),
            "prevHandX": round(prev_hand_x, 4),
            "prevHandY": round(prev_hand_y, 4),
            "velocityX": round(velocity_x, 4),
            "velocityY": round(velocity_y, 4),
            "gesture": gesture,
            "confidence": round(confidence, 4),
            "handDetected": True,
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


def is_finger_extended(points, tip_index: int, pip_index: int) -> bool:
    return points[tip_index].y < points[pip_index].y


def is_thumb_extended(points, handedness_label: str) -> bool:
    thumb_tip_x = points[4].x
    thumb_ip_x = points[3].x
    if handedness_label == "Right":
        return thumb_tip_x < thumb_ip_x
    return thumb_tip_x > thumb_ip_x


def landmark_distance(point_a, point_b) -> float:
    dx = point_a.x - point_b.x
    dy = point_a.y - point_b.y
    return (dx * dx + dy * dy) ** 0.5


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

    if index_extended and middle_extended and not ring_extended and not pinky_extended:
        if not thumb_extended or extended_count <= 3:
            return "two", "peace", 0.95

    if index_extended and not middle_extended and not ring_extended and not pinky_extended:
        if not thumb_extended or extended_count <= 2:
            return "one", "pointing", 0.94

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
        num_hands=1,
        min_hand_detection_confidence=0.6,
        min_hand_presence_confidence=0.6,
        min_tracking_confidence=0.6,
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


def extract_primary_hand(result):
    if not result or not result.hand_landmarks:
        return None

    handedness_label = "Right"
    if result.handedness and result.handedness[0]:
        handedness_label = result.handedness[0][0].category_name or "Right"

    return result.hand_landmarks[0], handedness_label


async def stream_gestures():
    LOGGER.info("WebSocket 服务已启动，等待 Java 连接...")

    cap = open_camera()
    state = GestureState()

    try:
        with create_landmarker() as hand_landmarker:
            while True:
                try:
                    async with websockets.connect(SERVER_URL, max_size=None) as websocket:
                        LOGGER.info("已连接到 Java WebSocket 服务: %s", SERVER_URL)

                        while True:
                            loop_start = time.perf_counter()
                            now = time.time()
                            ok, frame = cap.read()
                            if not ok:
                                await asyncio.sleep(0.02)
                                continue

                            frame = cv2.flip(frame, 1)
                            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                            mp_image = MP_IMAGE(image_format=MP_IMAGE_FORMAT.SRGB, data=rgb)
                            result = hand_landmarker.detect_for_video(
                                mp_image,
                                int(now * 1000),
                            )
                            first_hand = extract_primary_hand(result)

                            payload = state.build_payload(first_hand)
                            output_frame = frame
                            if SHOW_PREVIEW or SEND_IMAGE_STREAM:
                                output_frame = frame.copy()
                                draw_preview(
                                    output_frame,
                                    first_hand[0] if first_hand is not None else None,
                                    payload,
                                    state.last_raw_gesture,
                                )

                            if SEND_IMAGE_STREAM:
                                if now - state.last_image_send_time >= 1.0 / IMAGE_STREAM_FPS:
                                    state.last_encoded_image = encode_image_data(output_frame)
                                    state.last_image_send_time = now
                                payload["image_data"] = state.last_encoded_image

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
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    try:
        asyncio.run(stream_gestures())
    except KeyboardInterrupt:
        LOGGER.info("已手动停止 gesture_server.py")
