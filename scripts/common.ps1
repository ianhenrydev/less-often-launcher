$ErrorActionPreference = 'Stop'
$script:AppId = 'com.lessoften.launcher'
$script:ColorKeys = @('accessibility_display_daltonizer', 'accessibility_display_daltonizer_enabled')
function Find-Adb {
    $local = Join-Path $PSScriptRoot '../.tools/android-sdk/platform-tools/adb.exe'
    if (Test-Path -LiteralPath $local) { return (Resolve-Path $local).Path }
    if ($env:ANDROID_HOME) {
        $sdkAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $sdkAdb) { return $sdkAdb }
    }
    return (Get-Command adb -ErrorAction Stop).Source
}
$script:Adb = Find-Adb
function Invoke-Adb {
    $result = & $script:Adb @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($args[0])" }
    return $result
}
function Assert-Pixel {
    $devices = @(Invoke-Adb devices | Where-Object { $_ -match '\sdevice$' })
    if ($devices.Count -ne 1) { throw 'Connect exactly one authorized phone with USB debugging enabled.' }
    $model = (Invoke-Adb shell getprop ro.product.model).Trim()
    $api = (Invoke-Adb shell getprop ro.build.version.sdk).Trim()
    $release = (Invoke-Adb shell getprop ro.build.version.release).Trim()
    if ($model -ne 'Pixel 6a' -or $api -ne '37') { throw "Expected Pixel 6a API 37; found $model / Android $release / API $api" }
    Write-Output "Verified Pixel 6a / Android $release / API $api (device identifier omitted)."
}
