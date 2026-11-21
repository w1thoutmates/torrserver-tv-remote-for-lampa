param([string]$Sdk=$env:ANDROID_HOME, [string]$KeyStore=$env:TORR_TV_KEYSTORE)
$ErrorActionPreference='Stop'
if (!$Sdk -or !$KeyStore -or !$env:TORR_TV_KEY_PASSWORD) {throw 'Set ANDROID_HOME, TORR_TV_KEYSTORE and TORR_TV_KEY_PASSWORD before building.'}
$KeyStore=(Resolve-Path -LiteralPath $KeyStore).Path
function Run($tool, [string[]]$items) { & $tool @items; if($LASTEXITCODE){throw "$tool failed: $LASTEXITCODE"} }
$b="$Sdk/build-tools/35.0.0"
$android="$Sdk/platforms/android-34/android.jar"
$tmp=New-Item -Type Directory (Join-Path $env:TEMP ("torr-tv-"+[guid]::NewGuid()))
Push-Location $tmp
try {
 Run javac @('-encoding','utf-8','--release','8','-classpath',$android,'-d','.',"$PSScriptRoot/Remote.java")
 Run "$b/d8.bat" (@('--min-api','23','--lib',$android,'--output','.') + @(Get-ChildItem -Recurse *.class | ForEach-Object FullName))
 Run "$b/aapt.exe" @('package','-f','-M',"$PSScriptRoot/AndroidManifest.xml",'-S',"$PSScriptRoot/res",'-I',$android,'-F','raw.apk')
 Run "$b/aapt.exe" @('add','raw.apk','classes.dex')
 Run "$b/zipalign.exe" @('-f','4','raw.apk','aligned.apk')
 Run "$b/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass','env:TORR_TV_KEY_PASSWORD','--v4-signing-enabled','false','--out',"$PSScriptRoot/TorrRemote.apk",'aligned.apk')
 Run "$b/apksigner.bat" @('verify',"$PSScriptRoot/TorrRemote.apk")
} finally {
 Pop-Location
 $resolved=(Resolve-Path -LiteralPath $tmp.FullName).Path
 if ((Split-Path $resolved -Parent) -eq (Resolve-Path $env:TEMP).Path -and (Split-Path $resolved -Leaf) -match '^torr-tv-[a-f0-9-]{36}$') {
     Remove-Item -LiteralPath $resolved -Recurse -Force
 }
}

