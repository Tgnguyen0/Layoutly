param([ValidateSet('cuda_v12','cuda_v13','cpu')][string]$Backend = 'cuda_v12', [switch]$Restart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $env:LOCALAPPDATA 'Programs\Ollama'
$ollamaExecutable = Join-Path $runtimeRoot 'ollama.exe'
if (!(Test-Path $ollamaExecutable)) { throw 'Ollama is not installed for this user.' }
$logDirectory = Join-Path $projectRoot '.local-ai'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
if ($Restart) {
  foreach ($processItem in @(Get-Process -Name 'ollama app','ollama' -ErrorAction SilentlyContinue)) {
    if ($processItem.Path -and $processItem.Path.StartsWith($runtimeRoot, [StringComparison]::OrdinalIgnoreCase)) {
      Stop-Process -Id $processItem.Id -Force
      $processItem.WaitForExit(10000) | Out-Null
    }
  }
}
$env:OLLAMA_NO_CLOUD='1'
$env:OLLAMA_HOST='127.0.0.1:11434'
$env:OLLAMA_MAX_LOADED_MODELS='1'
$env:OLLAMA_NUM_PARALLEL='1'
$env:OLLAMA_MAX_QUEUE='1'
$env:OLLAMA_CONTEXT_LENGTH='4096'
$env:OLLAMA_FLASH_ATTENTION='0'
$env:OLLAMA_LLM_LIBRARY=$Backend
$existingService = $null
try { $existingService = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 2 } catch { }
if ($existingService) { Write-Output "Ollama already listening: $($existingService.version)"; exit 0 }
$serviceProcess = Start-Process -FilePath $ollamaExecutable -ArgumentList 'serve' -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory "ollama-$Backend.stdout.log") -RedirectStandardError (Join-Path $logDirectory "ollama-$Backend.stderr.log") -PassThru
$serviceProcess.Id | Set-Content (Join-Path $logDirectory 'ollama.pid')
for ($attempt=0; $attempt -lt 30; $attempt++) {
  try { $version = Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 1; Write-Output "Ollama $($version.version), PID $($serviceProcess.Id), requested backend $Backend, local-only"; exit 0 } catch { }
  $serviceProcess.Refresh()
  if ($serviceProcess.HasExited) { throw "Ollama exited; inspect .local-ai/ollama-$Backend.stderr.log" }
  Start-Sleep -Milliseconds 300
}
throw 'Ollama did not become reachable within the startup deadline.'
