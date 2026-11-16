' DJ Desktop Studio - Silent Launcher
Option Explicit
Dim WshShell, fso, scriptDir, batPath
Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
batPath = Chr(34) & scriptDir & "\run-dj-desktop.bat" & Chr(34)
WshShell.Run "cmd.exe /c " & batPath, 0, False
