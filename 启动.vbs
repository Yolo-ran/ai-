Set ws  = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
base   = fso.GetParentFolderName(WScript.ScriptFullName)
ws.CurrentDirectory = base
ws.Run "mvnw.cmd javafx:run", 0, False
