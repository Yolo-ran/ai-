import asyncio
import json
import logging
import time

import cv2
import mediapipe as mp
import websockets


SERVER_URL = "ws://127.0.0.1:8765"
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
SEND_FPS = 30
RECONNECT_DELAY_SECONDS = 1.5
SHOW_PREVIEW = True
PRINT_PAYLOAD_SAMPLE = True


logging.basicConfig(level=logging.INFO, format="[Python] %(message)s")
LOGGER = logging.getLogger("gesture_server")

mp_hands = mp.solutions.hands
mp_draw = mp.solutions.drawing_utils
mp_styles = mp.solutions.drawing_styles


class GestureState:
    def __init__(self):
        self.prev_hand_x = 0.0
        self.prev_hand_y = 0.0
        self.previous_detected = False

    def build_payload(self, result) -> dict:
        if result is None:
            self.previous_detected = False
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

        gesture, confidence = classify_gesture(hand_landmarks, handedness_label)

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
    points = hand_landmarks.landmark
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


def classify_gesture(hand_landmarks, handedness_label: str):
    points = hand_landmarks.landmark

    thumb_extended = is_thumb_extended(points, handedness_label)
    index_extended = is_finger_extended(points, 8, 6)
    middle_extended = is_finger_extended(points, 12, 10)
    ring_extended = is_finger_extended(points, 16, 14)
    pinky_extended = is_finger_extended(points, 20, 18)

    extended_count = sum(
        [thumb_extended, index_extended, middle_extended, ring_extended, pinky_extended]
    )

    if index_extended and middle_extended and not ring_extended and not pinky_extended:
        if not thumb_extended or extended_count <= 3:
            return "peace", 0.95

    if index_extended and not middle_extended and not ring_extended and not pinky_extended:
        if not thumb_extended or extended_count <= 2:
            return "pointing", 0.94

    if extended_count >= 4 and index_extended and middle_extended and ring_extended and pinky_extended:
        return "open", 0.98

    if extended_count <= 1 and not index_extended and not middle_extended and not ring_extended and not pinky_extended:
        return "fist", 0.97

    # 不确定时优先回退到 none，避免 Python 端误发错误基础手势。
    return "none", 0.55


def draw_preview(frame, hand_landmarks, payload):
    if hand_landmarks is not None:
        mp_draw.draw_landmarks(
            frame,
            hand_landmarks,
            mp_hands.HAND_CONNECTIONS,
            mp_styles.get_default_hand_landmarks_style(),
            mp_styles.get_default_hand_connections_style(),
        )

    cv2.putText(
        frame,
        f"gesture: {payload['gesture']}",
        (16, 32),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (120, 255, 120),
        2,
        cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        f"confidence: {payload['confidence']:.2f}",
        (16, 62),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (255, 200, 80),
        2,
        cv2.LINE_AA,
    )


async def stream_gestures():
    LOGGER.info("WebSocket 服务已启动，等待 Java 连接...")

    cap = cv2.VideoCapture(CAMERA_INDEX)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

    if not cap.isOpened():
        raise RuntimeError("默认摄像头打开失败")

    state = GestureState()

    try:
        with mp_hands.Hands(
            model_complexity=0,
            max_num_hands=1,
            min_detection_confidence=0.6,
            min_tracking_confidence=0.6,
        ) as hands:
            while True:
                try:
                    async with websockets.connect(SERVER_URL, max_size=None) as websocket:
                        LOGGER.info("已连接到 Java WebSocket 服务: %s", SERVER_URL)

                        while True:
                            loop_start = time.perf_counter()
                            ok, frame = cap.read()
                            if not ok:
                                await asyncio.sleep(0.02)
                                continue

                            frame = cv2.flip(frame, 1)
                            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                            result = hands.process(rgb)

                            first_hand = None
                            if result.multi_hand_landmarks and result.multi_handedness:
                                first_hand = (
                                    result.multi_hand_landmarks[0],
                                    result.multi_handedness[0].classification[0].label,
                                )

                            payload = state.build_payload(first_hand)
                            if PRINT_PAYLOAD_SAMPLE:
                                LOGGER.info("当前发送 JSON: %s", json.dumps(payload, ensure_ascii=True))
                            await websocket.send(json.dumps(payload, ensure_ascii=True))

                            if SHOW_PREVIEW:
                                draw_preview(
                                    frame,
                                    first_hand[0] if first_hand is not None else None,
                                    payload,
                                )
                                cv2.imshow("MediaPipe Gesture Server", frame)
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
