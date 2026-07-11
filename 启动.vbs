Set ws  = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
vbsPath = WScript.ScriptFullName
batPath = Left(vbsPath, Len(vbsPath) - 3) & "bat"
ws.CurrentDirectory = fso.GetParentFolderName(vbsPath)
ws.Run "cmd /c """ & batPath & """", 1, True
