Set ws  = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base   = fso.GetParentFolderName(WScript.ScriptFullName)

' ── 1. 检查 Java ──
javaOk = (ws.Run("cmd /c java -version >nul 2>&1", 0, True) = 0)

' ── 2. 检查 Python ──
pythonOk = (ws.Run("cmd /c python --version >nul 2>&1", 0, True) = 0)

If Not javaOk Then
    MsgBox "未检测到 Java 17+，请先安装 JDK 17。" & vbCrLf & "下载: https://adoptium.net/download/", 48, "缺少 Java"
    WScript.Quit 1
End If

If Not pythonOk Then
    MsgBox "未检测到 Python 3.10+。" & vbCrLf & "请先安装 Python 并确保已添加到 PATH。", 48, "缺少 Python"
    WScript.Quit 1
End If

' ── 3. 首次运行自动安装 Python 依赖 + 下载模型 ──
pipCmd  = "cmd /c cd /d " & Chr(34) & base & "\python" & Chr(34) & _
          " && python -c ""import mediapipe, cv2, websockets"" 2>nul || pip install -r requirements.txt -q"
ws.Run pipCmd, 0, True

modelPath = ws.ExpandEnvironmentStrings("%USERPROFILE%") & "\.mediapipe\hand_landmarker.task"
If Not fso.FileExists(modelPath) Then
    ws.Run "cmd /c python -c " & Chr(34) & _
           "import urllib.request,os;os.makedirs('" & Replace(modelPath, "\", "\\") & "\..',exist_ok=True);" & _
           "urllib.request.urlretrieve('https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task','" & _
           Replace(modelPath, "\", "\\") & "')" & Chr(34), 0, True
End If

' ── 4. 启动 Java（隐藏窗口）──
ws.Run "cmd /c cd /d " & Chr(34) & base & Chr(34) & " && mvnw.cmd javafx:run", 0, False

' ── 5. 等 Java 启动 ──
WScript.Sleep 10000

' ── 6. 启动 Python（隐藏窗口）──
ws.Run "cmd /c cd /d " & Chr(34) & base & "\python" & Chr(34) & " && pythonw gesture_server.py 2>nul || python gesture_server.py", 0, False
