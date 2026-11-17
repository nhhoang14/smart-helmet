$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$docker = 'C:/Program Files/Docker/Docker/resources/bin/docker.exe'
if (-not (Test-Path -LiteralPath $docker)) { $docker = 'docker' }
& $docker compose -p github-smart-helmet -f (Join-Path $project 'compose.yml') up -d mqtt
if ($LASTEXITCODE -ne 0) { throw 'Cannot start MQTT. Check Docker Desktop.' }
Write-Host 'MQTT TCP: 1883. ESP32 broker address is configured in src/local_config.h (current LAN: 10.72.117.44).'
