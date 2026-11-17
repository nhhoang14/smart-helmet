$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$baseCompose = Join-Path $project 'compose.yml'
$overrideCompose = Join-Path $project 'compose.run.yml'
if (-not (Test-Path -LiteralPath $baseCompose)) { throw 'Project compose.yml was not found.' }
$docker = 'C:/Program Files/Docker/Docker/resources/bin/docker.exe'
if (-not (Test-Path -LiteralPath $docker)) { $docker = 'docker' }
& $docker compose -p github-smart-helmet -f $baseCompose -f $overrideCompose up -d --build backend
if ($LASTEXITCODE -ne 0) { throw 'Cannot start the synced backend. Check Docker Desktop.' }
Write-Host 'Synced backend: http://localhost:8080; local React interface: http://localhost:5173 if running.'
