Set ws = CreateObject("Wscript.Shell")

' 启动 Python（后台，无窗口）
ws.Run "cmd /c cd /d " & Chr(34) & CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\python" & Chr(34) & " && pythonw gesture_server.py 2>nul || python gesture_server.py", 0, False

WScript.Sleep 2000

' 启动 Java（后台，无窗口）
ws.Run "cmd /c cd /d " & Chr(34) & CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & Chr(34) & " && mvnw.cmd javafx:run", 0, False
