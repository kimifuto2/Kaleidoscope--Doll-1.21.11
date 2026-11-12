param(
    [int]$Dx = 0,
    [int]$Dy = 0,
    [int]$MoveX = 0,
    [int]$MoveY = 0,
    [string]$Shot = "",
    [string]$Button = "left",
    [int]$Wheel = 0,
    [switch]$Click
)
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MCWin {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
  [DllImport("user32.dll")] public static extern IntPtr LoadKeyboardLayout(string pwszKLID, uint Flags);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
$p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -match 'Minecraft' } | Select-Object -First 1
if (-not $p) { Write-Host "NO_MC_WINDOW"; exit 1 }
[MCWin]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 700
$klid = [MCWin]::LoadKeyboardLayout("00000409", 1)
[MCWin]::PostMessage($p.MainWindowHandle, 0x0050, [IntPtr]::Zero, $klid) | Out-Null
Start-Sleep -Milliseconds 200
$r = New-Object MCWin+RECT
[MCWin]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
$left = $r.Left; $top = $r.Top
$w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
Write-Host ("WINDOW " + $left + "," + $top + " " + $w + "x" + $h)

if ($MoveX -ne 0 -or $MoveY -ne 0) {
    $cx = [int]($left + $w / 2)
    $cy = [int]($top + $h / 2)
    [MCWin]::SetCursorPos($cx, $cy) | Out-Null
    Start-Sleep -Milliseconds 150
    [MCWin]::mouse_event(0x0001, $MoveX, $MoveY, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 400
}
if ($Dx -ne 0 -or $Dy -ne 0) {
    [MCWin]::SetCursorPos([int]($left + $Dx), [int]($top + $Dy)) | Out-Null
    Start-Sleep -Milliseconds 200
    if ($Button -eq "right") {
        [MCWin]::mouse_event(0x0008, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
        [MCWin]::mouse_event(0x0010, 0, 0, 0, [UIntPtr]::Zero)
    } else {
        [MCWin]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
        [MCWin]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    }
    Start-Sleep -Milliseconds 500
}
if ($Click) {
    [MCWin]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
    if ($Button -eq "right") {
        [MCWin]::mouse_event(0x0008, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
        [MCWin]::mouse_event(0x0010, 0, 0, 0, [UIntPtr]::Zero)
    } else {
        [MCWin]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
        [MCWin]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    }
    Start-Sleep -Milliseconds 500
}
if ($Wheel -ne 0) {
    [MCWin]::SetCursorPos([int]($left + $w / 2), [int]($top + 260)) | Out-Null
    Start-Sleep -Milliseconds 200
    [MCWin]::mouse_event(0x0800, 0, 0, [uint32]($Wheel * 120), [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 400
}
if ($Shot -ne "") {
    Start-Sleep -Milliseconds 400
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($left, $top, 0, 0, $bmp.Size)
    $bmp.Save($Shot)
    $g.Dispose(); $bmp.Dispose()
    Write-Host ("SHOT " + $Shot)
}
