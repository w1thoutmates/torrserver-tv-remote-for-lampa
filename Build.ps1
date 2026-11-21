$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
foreach ($name in 'Controller','Tray') {
    $sources = @((Join-Path $PSScriptRoot "$name.cs"), (Join-Path $PSScriptRoot 'NetworkConfig.cs'))
    & $compiler /nologo /optimize+ /target:winexe /r:System.Xml.Linq.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Web.Extensions.dll "/out:$PSScriptRoot\$name.exe" $sources
    if ($LASTEXITCODE) { throw "Compilation failed: $name" }
}
