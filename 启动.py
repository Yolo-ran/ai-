"""
AI 手势游戏大厅 — 一键启动器（无终端版）
双击 启动.vbs 调用此脚本，不弹出任何终端窗口。
"""
import subprocess
import sys
import os
import time
import ctypes
import urllib.request
from pathlib import Path

BASE = Path(__file__).resolve().parent


def alert(title, msg):
    """弹出系统提示框"""
    ctypes.windll.user32.MessageBoxW(0, msg, title, 0x30)


def check(cmd):
    try:
        subprocess.run(cmd, capture_output=True, timeout=10,
                       creationflags=subprocess.CREATE_NO_WINDOW)
        return True
    except Exception:
        return False


def main():
    # 1. 检测 Java
    if not check(["java", "-version"]):
        alert("缺少 Java", "未找到 Java 17+。\n请先安装 JDK 17：\nhttps://adoptium.net/download/")
        sys.exit(1)

    # 2. 安装 Python 依赖
    python_dir = BASE / "python"
    req_file = python_dir / "requirements.txt"
    try:
        import mediapipe, cv2, websockets  # noqa
    except ImportError:
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-r", str(req_file), "-q"],
            cwd=str(python_dir),
            creationflags=subprocess.CREATE_NO_WINDOW,
        )

    # 3. 自动下载模型
    model_path = Path.home() / ".mediapipe" / "hand_landmarker.task"
    if not model_path.exists():
        model_path.parent.mkdir(parents=True, exist_ok=True)
        url = ("https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
               "hand_landmarker/float16/latest/hand_landmarker.task")
        try:
            urllib.request.urlretrieve(url, model_path)
        except Exception:
            pass  # Python 脚本内部也会重试

    # 4. 启动 Java
    subprocess.Popen(
        ["cmd", "/c", "cd", "/d", str(BASE), "&&", "mvnw.cmd", "javafx:run"],
        creationflags=subprocess.CREATE_NO_WINDOW,
    )

    # 5. 等 Java 启动
    time.sleep(15)

    # 6. 启动 Python 手势识别
    subprocess.Popen(
        [sys.executable, str(python_dir / "gesture_server.py")],
        cwd=str(python_dir),
        creationflags=subprocess.CREATE_NO_WINDOW,
    )


if __name__ == "__main__":
    main()
