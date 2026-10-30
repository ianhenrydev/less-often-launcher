param([string]$Apk = "$PSScriptRoot/../app/build/outputs/apk/debug/app-debug.apk",
      [string]$Baseline = "$PSScriptRoot/../artifacts/display-baseline.private.json")
. "$PSScriptRoot/common.ps1"
Assert-Pixel
if (!(Test-Path -LiteralPath $Apk)) { throw 'Build the APK first: .\gradlew.bat :app:assembleDebug' }
if (!(Test-Path -LiteralPath $Baseline)) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $Baseline) -Force | Out-Null
    $settings = [ordered]@{}
    foreach ($key in $script:ColorKeys) {
        $value = (Invoke-Adb shell settings get secure $key).Trim()
        $settings[$key] = if ($value -eq 'null') { $null } else { $value }
    }
    [ordered]@{ schemaVersion = 1; settings = $settings } | ConvertTo-Json | Set-Content -LiteralPath $Baseline -Encoding utf8
    Write-Output 'Recorded the exact color baseline in the ignored private recovery file.'
}
Invoke-Adb install -r $Apk
Invoke-Adb shell pm grant $script:AppId android.permission.WRITE_SECURE_SETTINGS
Invoke-Adb shell am start -n "$script:AppId/.SetupActivity"
Write-Output 'On the phone: allow restricted settings if prompted, enable Less Often accessibility, then choose Less Often as Home. Follow docs/acceptance.md before daily use.'
