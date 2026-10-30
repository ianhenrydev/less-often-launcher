param([switch]$Install)
$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/.."
try {
    if (!$env:JAVA_HOME) {
        $bundled = Get-ChildItem .tools/jdk -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($bundled) { $env:JAVA_HOME = $bundled.FullName }
    }
    & ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
    if ($Install) { & "$PSScriptRoot/setup-device.ps1" }
} finally { Pop-Location }
