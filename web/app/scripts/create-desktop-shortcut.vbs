' DJ Desktop Studio - VBScript Shortcut Generator Fallback
Option Explicit
Dim WshShell, fso, scriptDir, appDir, desktopPath, shortcutFile, shortcut, iconPath, launcherVbs

Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
appDir = fso.GetParentFolderName(scriptDir)

desktopPath = WshShell.SpecialFolders("Desktop")
shortcutFile = desktopPath & "\DJ Desktop Studio.lnk"
launcherVbs = scriptDir & "\run-dj-desktop.vbs"

Set shortcut = WshShell.CreateShortcut(shortcutFile)
If fso.FileExists(launcherVbs) Then
    shortcut.TargetPath = "wscript.exe"
    shortcut.Arguments = Chr(34) & launcherVbs & Chr(34)
Else
    shortcut.TargetPath = scriptDir & "\run-dj-desktop.bat"
    shortcut.WindowStyle = 7
End If

shortcut.WorkingDirectory = appDir
shortcut.Description = "DJ Desktop Studio - Professional Audio Player & DJ Mixer"

iconPath = appDir & "\public\icon.ico"
If fso.FileExists(iconPath) Then
    shortcut.IconLocation = iconPath & ",0"
Else
    iconPath = appDir & "\dist\icon.ico"
    If fso.FileExists(iconPath) Then
        shortcut.IconLocation = iconPath & ",0"
    Else
        iconPath = scriptDir & "\icon.ico"
        If fso.FileExists(iconPath) Then
            shortcut.IconLocation = iconPath & ",0"
        End If
    End If
End If

shortcut.Save
