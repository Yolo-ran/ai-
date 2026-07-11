Set ws = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

rootDir = fso.GetParentFolderName(WScript.ScriptFullName)
launcherBat = rootDir & "\启动.bat"

' 静默调用批处理启动器，具体逻辑统一维护在 启动.bat 中。
ws.Run "cmd /c " & Chr(34) & launcherBat & Chr(34), 0, False
