param([string]$EnvFile = "", [int]$Port = 8080)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot ".env" }
if (Test-Path -LiteralPath $EnvFile) {
    foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile))) {
        if ($line -match '^\s*((?:DATABASE_|LAYOUTLY_|MAIL_|OLLAMA_)[A-Z0-9_]+|FIGMA_TOKEN)=(.*)$') {
            $settingName = $matches[1]
            $settingValue = $matches[2].Trim()
            if ($settingValue.Length -ge 2 -and (
                ($settingValue.StartsWith('"') -and $settingValue.EndsWith('"')) -or
                ($settingValue.StartsWith("'") -and $settingValue.EndsWith("'")))) {
                $settingValue = $settingValue.Substring(1, $settingValue.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($settingName, $settingValue, "Process")
        }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin/java.exe"))) {
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
}
$mavenCommand = Get-Command mvn.cmd -ErrorAction Stop
Push-Location (Join-Path $projectRoot "layoutly-be")
try {
    & $mavenCommand.Source spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally { Pop-Location }
