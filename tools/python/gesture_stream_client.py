import asyncio
import base64
import json
import math
from collections import deque

import cv2
import mediapipe as mp
import websockets


SERVER_URL = "ws://127.0.0.1:8765"
CAMERA_INDEX = 0
FRAME_WIDTH = 640
FRAME_HEIGHT = 480
JPEG_QUALITY = 72
SEND_FPS = 30

SWIPE_THRESHOLD = 0.12
CONFIRM_DISTANCE_THRESHOLD = 0.065
BACK_DISTANCE_THRESHOLD = 0.22
HISTORY_SIZE = 6


mp_hands = mp.solutions.hands
mp_draw = mp.solutions.drawing_utils
mp_styles = mp.solutions.drawing_styles


class GestureTracker:
    def __init__(self):
        self.index_history = deque(maxlen=HISTORY_SIZE)
        self.last_sent = "IDLE"
        self.hold_frames = 0

    def detect(self, hand_landmarks):
        if hand_landmarks is None:
            self.index_history.clear()
            self.last_sent = "IDLE"
            self.hold_frames = 0
            return "IDLE", 0.2

        points = hand_landmarks.landmark
        wrist = points[0]
        thumb_tip = points[4]
        index_tip = points[8]
        middle_tip = points[12]
        pinky_tip = points[20]

        self.index_history.append(index_tip.x)

        if len(self.index_history) >= HISTORY_SIZE:
            delta_x = self.index_history[-1] - self.index_history[0]
            if delta_x <= -SWIPE_THRESHOLD:
                self.index_history.clear()
                return "SWIPE_LEFT", min(0.99, abs(delta_x) * 4.0)
            if delta_x >= SWIPE_THRESHOLD:
                self.index_history.clear()
                return "SWIPE_RIGHT", min(0.99, abs(delta_x) * 4.0)

        thumb_index_distance = math.hypot(thumb_tip.x - index_tip.x, thumb_tip.y - index_tip.y)
        palm_open_distance = math.hypot(index_tip.x - pinky_tip.x, index_tip.y - pinky_tip.y)
        hand_height = abs(middle_tip.y - wrist.y) + 1e-6

        if thumb_index_distance < CONFIRM_DISTANCE_THRESHOLD:
            return self._stable("CONFIRM", 0.96)

        if palm_open_distance > BACK_DISTANCE_THRESHOLD and hand_height > 0.18:
            return self._stable("BACK", 0.92)

        return self._stable("IDLE", 0.60)

    def _stable(self, gesture, confidence):
        if self.last_sent == gesture:
            self.hold_frames += 1
        else:
            self.last_sent = gesture
            self.hold_frames = 1

        if gesture in {"CONFIRM", "BACK"} and self.hold_frames < 3:
            return "IDLE", 0.55
        return gesture, confidence


def encode_frame(frame):
    ok, buffer = cv2.imencode(
        ".jpg",
        frame,
        [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY],
    )
    if not ok:
        return ""
    encoded = base64.b64encode(buffer.tobytes()).decode("ascii")
    return f"data:image/jpeg;base64,{encoded}"


async def stream():
    cap = cv2.VideoCapture(CAMERA_INDEX)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

    tracker = GestureTracker()

    with mp_hands.Hands(
        model_complexity=0,
        max_num_hands=1,
        min_detection_confidence=0.6,
        min_tracking_confidence=0.6,
    ) as hands:
        async with websockets.connect(SERVER_URL, max_size=None) as ws:
            print(f"[Python] 已连接到 {SERVER_URL}")

            while True:
                ok, frame = cap.read()
                if not ok:
                    await asyncio.sleep(0.02)
                    continue

                frame = cv2.flip(frame, 1)
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = hands.process(rgb)

                gesture = "IDLE"
                confidence = 0.5

                if result.multi_hand_landmarks:
                    hand_landmarks = result.multi_hand_landmarks[0]
                    mp_draw.draw_landmarks(
                        frame,
                        hand_landmarks,
                        mp_hands.HAND_CONNECTIONS,
                        mp_styles.get_default_hand_landmarks_style(),
                        mp_styles.get_default_hand_connections_style(),
                    )
                    gesture, confidence = tracker.detect(hand_landmarks)
                else:
                    tracker.index_history.clear()
                    gesture, confidence = "IDLE", 0.2

                cv2.putText(
                    frame,
                    f"Gesture: {gesture}",
                    (16, 36),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.9,
                    (120, 255, 120),
                    2,
                    cv2.LINE_AA,
                )
                cv2.putText(
                    frame,
                    f"Confidence: {confidence:.2f}",
                    (16, 72),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    (56, 189, 248),
                    2,
                    cv2.LINE_AA,
                )

                payload = {
                    "gesture": gesture,
                    "confidence": round(confidence, 3),
                    "image_data": encode_frame(frame),
                }
                await ws.send(json.dumps(payload))

                cv2.imshow("Gesture Stream Client", frame)
                if cv2.waitKey(1) & 0xFF == 27:
                    break

                await asyncio.sleep(1 / SEND_FPS)

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    try:
        asyncio.run(stream())
    except KeyboardInterrupt:
        print("[Python] 已手动停止串流")
