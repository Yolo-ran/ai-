# ai-

## 团队首次安装手势引擎

1. 拉取仓库最新 `main`。
2. 双击 `安装手势引擎.bat`，等待依赖和双手模型验证完成。
3. 以后直接双击 `启动.bat`。

识别环境统一为 Python 3.10–3.13、MediaPipe 0.10.35、OpenCV 5.0.0.93、websockets 16.0。
仓库中的 `python/gesture_server.py` 是唯一正式引擎，已配置 `num_hands=2`。旧入口
`tools/python/gesture_stream_client.py` 会自动转到正式引擎，不再运行旧的单手识别代码。
