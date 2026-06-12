$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot

try {
    Write-Host "Running Maven compile gate..."
    & mvn -B -ntp -DskipTests clean compile
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $qodanaCommand = Get-Command qodana -ErrorAction SilentlyContinue
    if ($null -eq $qodanaCommand) {
        [Console]::Error.WriteLine("Qodana CLI was not found on PATH. Install Qodana CLI, then rerun scripts/qodana-local.ps1.")
        exit 127
    }

    Write-Host "Running Qodana scan..."
    & $qodanaCommand.Source scan --show-report
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
