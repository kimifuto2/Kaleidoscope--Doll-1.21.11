param(
    [int]$Vk = 0x45,
    [switch]$NoClick
)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MCKey {
  [StructLayout(LayoutKind.Sequential)]
  public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit)]
  public struct INPUTUNION { [FieldOffset(0)] public KEYBDINPUT ki; }
  [StructLayout(LayoutKind.Sequential)]
  public struct INPUT { public uint type; public INPUTUNION u; }
  [DllImport("user32.dll")] public static extern uint SendInput(uint n, INPUT[] inputs, int size);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
  [DllImport("user32.dll")] public static extern IntPtr LoadKeyboardLayout(string pwszKLID, uint Flags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  public static void Key(ushort vk) {
    INPUT[] arr = new INPUT[2];
    arr[0].type = 1; arr[0].u.ki.wVk = vk; arr[0].u.ki.wScan = (ushort)MapVirtualKey(vk, 0); arr[0].u.ki.dwFlags = 0; arr[0].u.ki.time = 0; arr[0].u.ki.dwExtraInfo = IntPtr.Zero;
    arr[1] = arr[0];
    arr[1].u.ki.dwFlags = 0x0002;
    SendInput(2, arr, Marshal.SizeOf(typeof(INPUT)));
  }
}
"@
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $p) { Write-Host "NO_MC_WINDOW"; exit 1 }
[void][MCKey]::SetForegroundWindow($p.MainWindowHandle)
Start-Sleep -Milliseconds 500
$klid = [MCKey]::LoadKeyboardLayout("00000409", 1)
[MCKey]::PostMessage($p.MainWindowHandle, 0x0050, [IntPtr]::Zero, $klid) | Out-Null
Start-Sleep -Milliseconds 200
$r = New-Object MCKey+RECT
[void][MCKey]::GetWindowRect($p.MainWindowHandle, [ref]$r)
if (-not $NoClick) {
    [MCKey]::SetCursorPos([int](($r.Left+$r.Right)/2), [int](($r.Top+$r.Bottom)/2)) | Out-Null
    Start-Sleep -Milliseconds 150
    [MCKey]::mouse_event(0x0002,0,0,0,[UIntPtr]::Zero)
    [MCKey]::mouse_event(0x0004,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 600
}
$fg = [MCKey]::GetForegroundWindow()
Write-Host ("FG=" + $fg + " MC=" + $p.MainWindowHandle + " focused=" + ($fg -eq $p.MainWindowHandle))
[MCKey]::Key([ushort]$Vk)
Write-Host ("KEY " + $Vk)
