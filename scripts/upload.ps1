param([string]$Port = '')
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$platformio = Join-Path $env:USERPROFILE '.platformio/penv/Scripts/platformio.exe'
if (-not (Test-Path -LiteralPath $platformio)) { $platformio = 'pio' }
$arguments = @('run', '-d', $project, '-e', 'esp32dev', '-t', 'upload')
if ($Port) { $arguments += @('--upload-port', $Port) }
& $platformio @arguments
if ($LASTEXITCODE -ne 0) { throw 'Upload failed. Close Serial Monitor. If Connecting fails, hold BOOT and tap EN, then retry.' }
