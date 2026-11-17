param([string]$Port = '')
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$platformio = Join-Path $env:USERPROFILE '.platformio/penv/Scripts/platformio.exe'
if (-not (Test-Path -LiteralPath $platformio)) { $platformio = 'pio' }
$arguments = @('device', 'monitor', '-d', $project, '--baud', '115200')
if ($Port) { $arguments += @('--port', $Port) }
& $platformio @arguments
