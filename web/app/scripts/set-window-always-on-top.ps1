param(
    [Parameter(Mandatory = $true)][string]$WindowTitle,
    [Parameter(Mandatory = $true)][bool]$Enabled
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class DJDesktopWindowPin {
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc callback, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int maxCount);

    [DllImport("user32.dll")]
    public static extern int GetWindowTextLength(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int x, int y, int cx, int cy, uint flags);

    public static int SetTopmostByTitle(string title, bool enabled) {
        IntPtr target = IntPtr.Zero;
        EnumWindows((hWnd, lParam) => {
            if (!IsWindowVisible(hWnd)) return true;
            int length = GetWindowTextLength(hWnd);
            if (length <= 0) return true;
            StringBuilder text = new StringBuilder(length + 1);
            GetWindowText(hWnd, text, text.Capacity);
            if (text.ToString().IndexOf(title, StringComparison.OrdinalIgnoreCase) >= 0) {
                target = hWnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);

        if (target == IntPtr.Zero) return 2;
        IntPtr insertAfter = enabled ? new IntPtr(-1) : new IntPtr(-2);
        const uint flags = 0x0001 | 0x0002 | 0x0040; // NOMOVE | NOSIZE | SHOWWINDOW
        return SetWindowPos(target, insertAfter, 0, 0, 0, 0, flags) ? 0 : 3;
    }
}
'@

$result = [DJDesktopWindowPin]::SetTopmostByTitle($WindowTitle, $Enabled)
if ($result -eq 2) {
    Write-Error "The control panel window was not found. Ensure it is open and retry."
    exit 2
}
if ($result -ne 0) {
    Write-Error "Windows could not change the control panel topmost state (Win32 code $result)."
    exit $result
}
exit 0
