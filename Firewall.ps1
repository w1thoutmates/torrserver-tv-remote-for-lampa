param([ValidateSet('Install','Uninstall','Status')][string]$Action = 'Status')
$ErrorActionPreference = 'Stop'
if ($Action -eq 'Install') {
    $cfg = ([xml](Get-Content (Join-Path $PSScriptRoot 'network.xml') -Raw)).Network
    [byte[]]$bytes = ([ipaddress]$cfg.Address).GetAddressBytes()
    $prefix = [int]$cfg.Prefix
    if ($bytes.Length -ne 4 -or $prefix -lt 1 -or $prefix -gt 32) { throw 'Invalid IPv4 prefix' }
    for ($i=0; $i -lt 4; $i++) {
        $bits = [Math]::Max(0,[Math]::Min(8,$prefix-8*$i))
        $bytes[$i] = $bytes[$i] -band (255 -shl (8-$bits))
    }
    $subnet = ($bytes -join '.') + '/' + $prefix
}
foreach ($port in 8090,8091) {
    $name = "PublicTorrServerMatrix.Private.TCP.$port"
    if ($Action -eq 'Status') {
        netsh advfirewall firewall show rule "name=$name" verbose
        continue
    }
    $rule = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
    $exe = if ($port -eq 8090) { "TorrServer-matrix-windows-amd64.exe" } else { "Controller.exe" }
    if ($rule -and ($rule | Get-NetFirewallApplicationFilter).Program -ne (Join-Path $PSScriptRoot $exe)) { throw "Another installation owns $name" }
    if ($rule) { Remove-NetFirewallRule -Name $name }
    if ($Action -eq 'Install') {
        $exe = if ($port -eq 8090) { 'TorrServer-matrix-windows-amd64.exe' } else { 'Controller.exe' }
        New-NetFirewallRule -Name $name -DisplayName $name -Direction Inbound -Action Allow -Profile Private -Protocol TCP -LocalAddress $cfg.Address -LocalPort $port -RemoteAddress $subnet -InterfaceAlias $cfg.Interface -Program (Join-Path $PSScriptRoot $exe) | Out-Null
    }
}

