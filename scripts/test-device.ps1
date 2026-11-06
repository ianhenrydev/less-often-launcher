param([ValidateSet('display', 'usage', 'history', 'segment', 'smoke', 'ten-minute', 'notification', 'launcher', 'color-transitions')][string]$Mode = 'smoke')
. "$PSScriptRoot/common.ps1"
Assert-Pixel -ValidatedDeviceOnly
Invoke-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
$root = (Resolve-Path "$PSScriptRoot/..").Path
$appTargeted = $Mode -in @('display', 'usage', 'history', 'segment')
$gatePattern = '^com\.lessoften\.launcher/(?:\.GateService|com\.lessoften\.launcher\.GateService)$'
function Get-EnabledServices {
    $value = (Invoke-Adb shell settings get secure enabled_accessibility_services | Out-String).Trim()
    if (!$value -or $value -eq 'null') { return @() }
    return @($value.Split(':') | Where-Object { $_ })
}
function Restore-GateBinding([string[]]$before) {
    $originalGate = @($before | Where-Object { $_ -match $gatePattern } | Select-Object -First 1)
    if (!$originalGate.Count) { return }
    $current = @(Get-EnabledServices)
    # Respect a service that was explicitly disabled while the test was running.
    if (!@($current | Where-Object { $_ -match $gatePattern }).Count) { return }
    $others = @($current | Where-Object { $_ -notmatch $gatePattern })
    try {
        if ($others.Count) { Invoke-Adb shell settings put secure enabled_accessibility_services ($others -join ':') | Out-Null }
        else { Invoke-Adb shell settings delete secure enabled_accessibility_services | Out-Null }
    } finally {
        $latest = @(Get-EnabledServices | Where-Object { $_ -notmatch $gatePattern })
        Invoke-Adb shell settings put secure enabled_accessibility_services (($latest + $originalGate) -join ':') | Out-Null
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $dump = (Invoke-Adb shell dumpsys accessibility | Out-String)
        $bound = [regex]::Match($dump, '(?s)Bound services:\{(.*?)\}\s*Enabled services:').Groups[1].Value
        if ($bound -match 'Service\[label=Less Often,') { Write-Output 'Less Often accessibility service rebound after instrumentation.'; return }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Less Often accessibility did not reconnect after instrumentation; reconnect it in Android accessibility settings before using Restricted apps.'
}
$beforeServices = if ($appTargeted) { @(Get-EnabledServices) } else { @() }
$instrumentationExit = $null
try {
    if ($appTargeted) {
        Invoke-Adb install -r "$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
        $instrumentationArgs = @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode, 'com.lessoften.launcher.test/com.lessoften.launcher.DisplayInstrumentation')
    } else {
        Invoke-Adb install -r "$root/probe/build/outputs/apk/debug/probe-debug.apk"
        Invoke-Adb install -r "$root/probe/build/outputs/apk/androidTest/debug/probe-debug-androidTest.apk"
        Invoke-Adb shell pm grant com.lessoften.probe android.permission.POST_NOTIFICATIONS
        $instrumentationArgs = @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode, 'com.lessoften.probe.test/com.lessoften.probe.ProbeInstrumentation')
    }
    & $script:Adb @instrumentationArgs | Tee-Object -Variable result
    $instrumentationExit = $LASTEXITCODE
} finally {
    if ($appTargeted) { Restore-GateBinding $beforeServices }
}
if ($instrumentationExit -ne 0) { throw 'ADB instrumentation command failed.' }
$text = $result -join "`n"
if ($text -match '(FAIL:|SKIPPED:)' -or $text -notmatch '(?m)^(INSTRUMENTATION_RESULT: stream=)?PASS:') { throw 'Device check did not pass.' }
Write-Output 'Inspect the instrumentation PASS/FAIL result above. The probe is a test-only package and is not part of the launcher APK.'
