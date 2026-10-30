param([ValidateSet('display', 'smoke', 'ten-minute', 'notification', 'launcher', 'color-transitions')][string]$Mode = 'smoke')
. "$PSScriptRoot/common.ps1"
Assert-Pixel
Invoke-Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
$root = (Resolve-Path "$PSScriptRoot/..").Path
if ($Mode -eq 'display') {
    Invoke-Adb install -r "$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    $instrumentationArgs = @('shell', 'am', 'instrument', '-w', 'com.lessoften.launcher.test/com.lessoften.launcher.DisplayInstrumentation')
} else {
    Invoke-Adb install -r "$root/probe/build/outputs/apk/debug/probe-debug.apk"
    Invoke-Adb install -r "$root/probe/build/outputs/apk/androidTest/debug/probe-debug-androidTest.apk"
    Invoke-Adb shell pm grant com.lessoften.probe android.permission.POST_NOTIFICATIONS
    $instrumentationArgs = @('shell', 'am', 'instrument', '-w', '-e', 'mode', $Mode, 'com.lessoften.probe.test/com.lessoften.probe.ProbeInstrumentation')
}
& $script:Adb @instrumentationArgs | Tee-Object -Variable result
if ($LASTEXITCODE -ne 0) { throw 'ADB instrumentation command failed.' }
$text = $result -join "`n"
if ($text -match '(FAIL:|SKIPPED:)' -or $text -notmatch '(?m)^(INSTRUMENTATION_RESULT: stream=)?PASS:') { throw 'Device check did not pass.' }
Write-Output 'Inspect the instrumentation PASS/FAIL result above. The probe is a test-only package and is not part of the launcher APK.'
