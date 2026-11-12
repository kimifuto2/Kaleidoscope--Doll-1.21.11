param(
    [string]$Text = "",
    [string]$TextFile = "",
    [switch]$NoChatOpen,
    [switch]$PressEnter = $true
)
if ([string]::IsNullOrEmpty($Text) -and -not [string]::IsNullOrEmpty($TextFile)) {
    $Text = (Get-Content -LiteralPath $TextFile -Raw).Trim()
}
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MCSend2 {
  [StructLayout(LayoutKind.Sequential)]
  public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit)]
  public struct INPUTUNION { [FieldOffset(0)] public KEYBDINPUT ki; }
  [StructLayout(LayoutKind.Sequential)]
  public struct INPUT { public uint type; public INPUTUNION u; }
  [DllImport("user32.dll", SetLastError=true)]
  public static extern uint SendInput(uint n, INPUT[] inputs, int size);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern IntPtr LoadKeyboardLayout(string id, uint flags);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }

  public static void Key(ushort vk, ushort scan) {
    INPUT[] arr = new INPUT[2];
    arr[0].type = 1; arr[0].u.ki.wVk = vk; arr[0].u.ki.wScan = scan; arr[0].u.ki.dwFlags = 0; arr[0].u.ki.time = 0; arr[0].u.ki.dwExtraInfo = IntPtr.Zero;
    arr[1] = arr[0];
    arr[1].u.ki.dwFlags = 0x0002;
    SendInput(2, arr, Marshal.SizeOf(typeof(INPUT)));
  }
  public static void Char(char c) {
    INPUT[] arr = new INPUT[2];
    arr[0].type = 1; arr[0].u.ki.wVk = 0; arr[0].u.ki.wScan = (ushort)c; arr[0].u.ki.dwFlags = 0x0004; arr[0].u.ki.time = 0; arr[0].u.ki.dwExtraInfo = IntPtr.Zero;
    arr[1] = arr[0];
    arr[1].u.ki.dwFlags = 0x0004 | 0x0002;
    SendInput(2, arr, Marshal.SizeOf(typeof(INPUT)));
  }
}
"@
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $p) { Write-Host "NO_MC_WINDOW"; exit 1 }
$hwnd = $p.MainWindowHandle
[void][MCSend2]::LoadKeyboardLayout("00000409", 1)
[void][MCSend2]::PostMessage($hwnd, 0x0050, [IntPtr]::Zero, [IntPtr]::Zero)
[void][MCSend2]::ShowWindow($hwnd, 9)
Start-Sleep -Milliseconds 300
[void][MCSend2]::SetForegroundWindow($hwnd)
Start-Sleep -Milliseconds 500
$r = New-Object MCSend2+RECT
[void][MCSend2]::GetWindowRect($hwnd, [ref]$r)
$cx = [int](($r.Left + $r.Right) / 2); $cy = [int](($r.Top + $r.Bottom) / 2)
[MCSend2]::SetCursorPos($cx, $cy) | Out-Null
Start-Sleep -Milliseconds 150
[MCSend2]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
[MCSend2]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 600
$fg = [MCSend2]::GetForegroundWindow()
Write-Host ("FG=" + $fg + " MC=" + $hwnd + " focused=" + ($fg -eq $hwnd))

if (-not $NoChatOpen) {
    [MCSend2]::Key(0x54, [MCSend2]::MapVirtualKey(0x54, 0))
    Start-Sleep -Milliseconds 500
}
foreach ($ch in $Text.ToCharArray()) {
    [MCSend2]::Char($ch)
    Start-Sleep -Milliseconds 15
}
Start-Sleep -Milliseconds 250
if ($PressEnter) {
    [MCSend2]::Key(0x0D, [MCSend2]::MapVirtualKey(0x0D, 0))
    Start-Sleep -Milliseconds 400
}
Write-Host ("SENT " + $Text)
