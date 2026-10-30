param([string]$Baseline = "$PSScriptRoot/../artifacts/display-baseline.private.json", [switch]$FromAppJournal)
. "$PSScriptRoot/common.ps1"
Assert-Pixel
$enabled = (Invoke-Adb shell settings get secure enabled_accessibility_services) -join ''
if ($enabled -match [regex]::Escape($script:AppId)) { throw 'First disable the Less Often accessibility service in Android Settings to prevent competing writes.' }
if ($FromAppJournal) {
    # Debug APK only. Parse privately; never print sessions or the preferences file.
    [xml]$xml = (Invoke-Adb exec-out run-as $script:AppId cat shared_prefs/runtime.xml) -join "`n"
    $active = $xml.map.string | Where-Object { $_.name -eq 'colorJournal' }
    if ($active.InnerText -ne 'active') { throw 'No active app display journal. Use the saved baseline file instead.' }
    $values = @{}
    foreach ($key in $script:ColorKeys) {
        $node = $xml.map.string | Where-Object { $_.name -eq "baseline:$key" }
        if (!$node) { throw "Journal missing $key; refusing guessed defaults." }
        $values[$key] = if ($node.InnerText -eq 'absent') { $null } else { $node.InnerText.Substring(1) }
    }
} else {
    $data = Get-Content -LiteralPath $Baseline -Raw | ConvertFrom-Json
    if ($data.schemaVersion -ne 1) { throw 'Unsupported recovery file.' }
    $values = @{}
    foreach ($key in $script:ColorKeys) {
        if ($key -notin $data.settings.PSObject.Properties.Name) { throw "Baseline missing $key; refusing guessed defaults." }
        $values[$key] = $data.settings.$key
    }
}
# Restore enabled first, then mode; preserve absence with delete, not a default.
foreach ($key in $script:ColorKeys) {
    if ($null -ne $values[$key] -and [string]$values[$key] -notmatch '^-?\d+$') { throw "Unexpected nonnumeric color value for $key" }
}
foreach ($key in @($script:ColorKeys[1], $script:ColorKeys[0])) {
    $value = $values[$key]
    if ($null -eq $value) { Invoke-Adb shell settings delete secure $key | Out-Null }
    else {
        Invoke-Adb shell settings put secure $key ([string]$value) | Out-Null
    }
    $actual = (Invoke-Adb shell settings get secure $key).Trim()
    $expected = if ($null -eq $value) { 'null' } else { [string]$value }
    if ($actual -ne $expected) { throw "Restoration verification failed for $key" }
}
Write-Output 'Recorded baseline restored exactly. In Less Often setup, choose Restore recorded display baseline to clear any reconciled journal before re-enabling accessibility.'
