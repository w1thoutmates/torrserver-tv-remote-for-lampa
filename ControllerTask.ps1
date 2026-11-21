param([ValidateSet('Install','Uninstall')][string]$Action,[string]$Account)
$ErrorActionPreference = 'Stop'
$name = 'Public MatriX Controller'
$task = Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
$path = Join-Path $PSScriptRoot 'Controller.exe'
if ($task -and $task.Actions[0].Execute -ne $path) { throw 'A different folder owns this task.' }
if ($Action -eq 'Uninstall') {
    if (Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue) {
        Stop-ScheduledTask -TaskName $name
        Unregister-ScheduledTask -TaskName $name -Confirm:$false
    }
    & (Join-Path $PSScriptRoot "Firewall.ps1") Uninstall
    exit
}
$path = Join-Path $PSScriptRoot 'Controller.exe'
& (Join-Path $PSScriptRoot "Firewall.ps1") Install
$command = New-ScheduledTaskAction -Execute $path -WorkingDirectory $PSScriptRoot
$principal = New-ScheduledTaskPrincipal -UserId $Account -LogonType S4U -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -RestartCount 10 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
Register-ScheduledTask -TaskName $name -Action $command -Trigger (New-ScheduledTaskTrigger -AtStartup) -Principal $principal -Settings $settings -Force | Out-Null
$task = Get-ScheduledTask -TaskName $name
if ($task.State -ne 'Running') {
    Get-Process Controller -ErrorAction SilentlyContinue | Where-Object Path -eq $path | Stop-Process
    Start-ScheduledTask -TaskName $name
}
Start-Sleep -Seconds 2
if ((Get-ScheduledTask -TaskName $name).State -ne 'Running') { throw 'Controller task did not start' }

