"""Compatibility launcher for the canonical dual-hand gesture engine.

Older project instructions started this file directly. Keep that command
working, but route it to python/gesture_server.py so nobody accidentally runs
the retired one-hand MediaPipe implementation.
"""

from pathlib import Path
import runpy


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ENGINE_PATH = PROJECT_ROOT / "python" / "gesture_server.py"


if __name__ == "__main__":
    runpy.run_path(str(ENGINE_PATH), run_name="__main__")
