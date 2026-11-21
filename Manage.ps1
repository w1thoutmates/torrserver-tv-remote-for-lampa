param([ValidateSet('Install','Uninstall','Status')][string]$Action = 'Status')
$ErrorActionPreference = 'Stop'
$startup = [Environment]::GetFolderPath('Startup')
$shell = New-Object -ComObject WScript.Shell
if ($Action -eq 'Uninstall' -and (Get-Process 'TorrServer-matrix-windows-amd64' -ErrorAction SilentlyContinue | Where-Object Path -eq (Join-Path $PSScriptRoot 'TorrServer-matrix-windows-amd64.exe'))) {
    throw 'Stop TorrServer before uninstalling.'
}
$taskName = 'Public MatriX Controller'
if ($Action -ne 'Status') {
    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}\ControllerTask.ps1" {1} "{2}"' -f $PSScriptRoot,$Action,$account
    $helper = Start-Process powershell.exe -WindowStyle Hidden -Verb RunAs -ArgumentList $arguments -PassThru -Wait
    if ($helper.ExitCode -ne 0) { throw "Controller task setup failed ($($helper.ExitCode))" }
}
foreach ($item in 'Tray') {
    $path = Join-Path $PSScriptRoot "$item.exe"
    $link = Join-Path $startup "Public MatriX $item.lnk"
    $process = Get-Process $item -ErrorAction SilentlyContinue | Where-Object Path -eq $path
    if ((Test-Path -LiteralPath $link) -and $shell.CreateShortcut($link).TargetPath -ne $path) { throw "Shortcut belongs to another installation: $link" }
    switch ($Action) {
        Install {
            if (!(Test-Path -LiteralPath $path)) { throw "Missing $path" }
            $shortcut = $shell.CreateShortcut($link)
            $shortcut.TargetPath = $path
            $shortcut.WorkingDirectory = $PSScriptRoot
            $shortcut.Save()
            if (!$process) { Start-Process $path -WorkingDirectory $PSScriptRoot }
        }
        Uninstall {
            if (Test-Path -LiteralPath $link) { Remove-Item -LiteralPath $link }
            $process | Stop-Process
        }
        Status { "$item : PID=$($process.Id) AtLogin=$(Test-Path -LiteralPath $link)" }
    }
}
if ($Action -eq 'Status') {
    schtasks /query /tn $taskName
    if (!(Get-Process Controller -ErrorAction SilentlyContinue | Where-Object Path -eq (Join-Path $PSScriptRoot "Controller.exe"))) { Write-Warning "This controller is stopped"; return }
    try { Invoke-RestMethod 'http://127.0.0.1:8091/status' -TimeoutSec 5 | ConvertTo-Json -Compress } catch { Write-Warning $_ }
    & (Join-Path $PSScriptRoot 'Firewall.ps1') Status
}

