Set ws  = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base   = fso.GetParentFolderName(WScript.ScriptFullName)
ws.CurrentDirectory = base
' 调用 启动.bat（含进程清理 + 条件镜像 + 错误可见 + pause），不使用静默隐藏
ws.Run "cmd /c """" & base & "\启动.bat""""", 1, True
