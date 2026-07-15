# ai-

## 本地账户与手势签入

程序启动后首先显示全屏视频账户页。首次使用请选择创建账户；以后使用用户名和密码登录，
验证通过后才会进入原有的 `ENTER` 握拳签入页。

账户默认保存在 Windows 的 `%LOCALAPPDATA%\AIGestureGame\data\users.db`。密码不会明文保存，
数据库仅记录随机盐和 PBKDF2-SHA256 摘要；也可通过系统属性 `gesturegame.data.dir` 覆盖数据目录。

## 团队首次安装手势引擎

1. 拉取仓库最新 `main`。
2. 双击 `安装手势引擎.bat`，等待依赖和双手模型验证完成。
3. 以后直接双击 `启动.bat`。

识别环境统一为 Python 3.10–3.13、MediaPipe 0.10.35、OpenCV 5.0.0.93、websockets 16.0。
仓库中的 `python/gesture_server.py` 是唯一正式引擎，已配置 `num_hands=2`。旧入口
`tools/python/gesture_stream_client.py` 会自动转到正式引擎，不再运行旧的单手识别代码。

## 猜拳对局记录

每场猜拳结束后会自动追加到 CSV，并在游戏界面显示累计胜、负、平和胜率。

- Windows：`%LOCALAPPDATA%\AIGestureGame\data\rps_records.csv`
- 其他系统：`~/.ai-gesture-game/data/rps_records.csv`

记录字段包括时间、难度、赛制局数、实际局数、双方比分、胜负结果及累计胜率。

## AI 动态关卡（星际突击）

“星际突击”是横向卷轴射击游戏：手的上下位置控制战机，战机自动射击。每局进入
Boss 阶段后，Java 会在后台根据命中率、受伤次数、分数和通关情况生成下一关 JSON；
接口无密钥、超时或返回异常时会自动切换到本地动态生成，不影响游戏运行。

推荐直接在程序登录页顶部或大厅左上角点击 `API 设置`，填写服务商、接口地址、模型和
API Key，并使用“测试连接”验证。设置会加密保存在本机，动态关卡和猜拳 AI 台词共用。

DeepSeek 默认配置为 `https://api.deepseek.com/chat/completions` 与 `deepseek-chat`。
其他提供 OpenAI Chat Completions 兼容接口的国内服务可选择“其他兼容接口”自行填写。

命令行环境变量仍作为开发调试兜底（PowerShell）：

```powershell
$env:DEEPSEEK_API_KEY="你的 API Key"
$env:DEEPSEEK_MODEL="deepseek-chat"
./启动.bat
```

还可用以下通用环境变量接入其他兼容 `/chat/completions` 的国内服务：

- `LLM_API_KEY`：API Key（优先级高于 `DEEPSEEK_API_KEY`）
- `LLM_API_URL`：完整的对话补全地址
- `LLM_MODEL`：模型名称

密钥只从环境变量读取，不会写入仓库。界面左上角会显示当前关卡来自 AI 还是本地规则。
