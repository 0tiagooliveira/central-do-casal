param(
    [string]$RuntimeRoot = 'D:/CodexBuild/central-do-casal-2026-10-10/orchestration',
    [switch]$RefreshGraph
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$omxEntry = Join-Path $RuntimeRoot 'omx-runtime/node_modules/oh-my-codex/dist/cli/omx.js'
$crgEntry = Join-Path $RuntimeRoot 'crg-python312/Scripts/code-review-graph.exe'
$env:CODEX_HOME = Join-Path $RuntimeRoot 'codex-home'
$env:OMX_ROOT = Join-Path $RuntimeRoot 'omx-state'
$env:OMX_RUNTIME_BINARY = Join-Path $RuntimeRoot 'native-cache/0.21.8/win32-x64/omx-runtime/omx-runtime.exe'
$env:OMX_AUTO_UPDATE = '0'
$env:CRG_HOME = Join-Path $RuntimeRoot 'crg-home'
$env:CRG_DATA_DIR = Join-Path $RuntimeRoot 'crg-data'
$env:PATH = (Join-Path $RuntimeRoot 'omx-runtime/node_modules/@ast-grep/cli-win32-x64-msvc') + ';' + $env:PATH
Remove-Item Env:CODEX_SESSION_ID -ErrorAction SilentlyContinue
Remove-Item Env:CODEX_THREAD_ID -ErrorAction SilentlyContinue
if (-not (Test-Path -LiteralPath $omxEntry)) { throw 'OMX isolado ausente. Consulte docs/ORQUESTRACAO.md.' }
if (-not (Test-Path -LiteralPath $crgEntry)) { throw 'CRG isolado ausente. Consulte docs/ORQUESTRACAO.md.' }
node $omxEntry doctor
node $omxEntry team api list-tasks --input '{"team_name":"central-review-backlog"}' --json
$astInput = @{ pattern = 'FirebaseAuth.getInstance()'; language = 'kotlin'; path = (Join-Path $projectRoot 'app/src/main'); maxResults = 20 } | ConvertTo-Json -Compress
node $omxEntry code-intel ast_grep_search --input $astInput --json
if ($RefreshGraph) { & $crgEntry update --repo $projectRoot }
& $crgEntry status --repo $projectRoot --json
& $crgEntry impact --repo $projectRoot --files 'app/src/main/java/com/centraldocasal/app/MainActivity.kt' 'app/src/main/java/com/centraldocasal/app/ImportedData.kt' --depth 2 --max-results 80
