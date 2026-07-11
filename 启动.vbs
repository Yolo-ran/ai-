Set ws  = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base   = fso.GetParentFolderName(WScript.ScriptFullName)

' 用 pythonw 运行启动脚本（无终端窗口）
ws.Run "cmd /c cd /d """ & base & """ && pythonw 启动.py", 0, False
