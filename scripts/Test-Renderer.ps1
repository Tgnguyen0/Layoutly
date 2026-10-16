param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$RunName,
    [switch]$CssCorrections,
    [switch]$PageConsistency,
    [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Baseline = ""
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$resultRoot = Join-Path $projectRoot '.local-build/fidelity'
$runOutput = Join-Path $resultRoot $RunName
if (Test-Path -LiteralPath $runOutput) { throw "Run already exists. Choose a fresh RunName; baselines are immutable." }
function Invoke-RendererTool([string]$Executable, [string[]]$ToolArguments) {
    # Windows PowerShell turns ordinary native stderr warnings into ErrorRecords.
    # Judge native commands by exit status; keep terminating errors for script operations.
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $Executable @ToolArguments; $toolExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $savedPreference }
    if ($toolExit -ne 0) { throw "$Executable failed with exit code $toolExit" }
}
New-Item -ItemType Directory -Force -Path $resultRoot | Out-Null
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-Command java.exe -ErrorAction Stop).Source)
}
$env:LAYOUTLY_FIDELITY_OUTPUT = $runOutput
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $projectRoot '.local-build/playwright-browsers'
if ($Baseline) {
    $baselineOutput = Join-Path $resultRoot $Baseline
    $env:LAYOUTLY_FIDELITY_LOCK = Join-Path $baselineOutput 'export-package-lock.json'
    if (-not (Test-Path -LiteralPath $env:LAYOUTLY_FIDELITY_LOCK)) { throw 'Baseline dependency lock is missing.' }
} else { Remove-Item Env:LAYOUTLY_FIDELITY_LOCK -ErrorAction SilentlyContinue }
Push-Location (Join-Path $projectRoot 'layoutly-be')
try {
    Invoke-RendererTool -Executable 'mvn.cmd' -ToolArguments @('-B',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-Dtest=RendererCorpusExportTest',"-Dlayoutly.fidelity.output=$runOutput","-Dlayoutly.fidelity.css-corrections=$($CssCorrections.IsPresent.ToString().ToLower())","-Dlayoutly.fidelity.page-consistency=$($PageConsistency.IsPresent.ToString().ToLower())",'test')
    Get-ChildItem src/main/java/com/tgnguyen/layoutlybe/service -Filter '*GeneratorService.java' | Get-FileHash -Algorithm SHA256 | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runOutput 'renderer-source.json')
} finally { Pop-Location }
Push-Location (Join-Path $projectRoot 'layoutly-fe')
try {
    Invoke-RendererTool -Executable 'npm.cmd' -ToolArguments @('run','test:fidelity:metrics')
    Invoke-RendererTool -Executable 'npx.cmd' -ToolArguments @('playwright','install','chromium')
    Invoke-RendererTool -Executable 'npm.cmd' -ToolArguments @('run','test:fidelity:build')
    Invoke-RendererTool -Executable 'npm.cmd' -ToolArguments @('run','test:fidelity')
    if ($Baseline) {
        Invoke-RendererTool -Executable 'node' -ToolArguments @('tests/fidelity/compare-runs.mjs',$baselineOutput,$runOutput)
    }
} finally { Pop-Location }
Write-Output "Results: $runOutput (measurement success does not imply 99% Figma fidelity)."
