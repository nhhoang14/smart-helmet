param([string]$EmscriptenRoot)

$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $projectPath '.pio\host-tests'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

if ($EmscriptenRoot) {
    $env:EM_CONFIG = Join-Path $EmscriptenRoot '.emscripten'
    & (Join-Path $EmscriptenRoot 'python\python.exe') (Join-Path $EmscriptenRoot 'emscripten\em++.py') `
        -std=c++14 -Wall -Wextra -I (Join-Path $projectPath 'src') `
        (Join-Path $projectPath 'src\fall_detector.cpp') (Join-Path $PSScriptRoot 'fall_detector_test.cpp') `
        (Join-Path $projectPath 'src\fall_alarm.cpp') (Join-Path $PSScriptRoot 'fall_alarm_test.cpp') `
        (Join-Path $projectPath 'src\push_button.cpp') (Join-Path $PSScriptRoot 'push_button_test.cpp') `
        -sENVIRONMENT=node -sWASM_ASYNC_COMPILATION=0 -o (Join-Path $outputPath 'fall_detector_test.js')
    if ($LASTEXITCODE -ne 0) { throw 'WebAssembly test compilation failed.' }
    & (Join-Path $EmscriptenRoot 'node\node.exe') (Join-Path $outputPath 'fall_detector_test.js')
    if ($LASTEXITCODE -ne 0) { throw 'Fall detector tests failed.' }
    exit 0
}

$vswherePath = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$installationPath = & $vswherePath -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $installationPath) { throw 'Install Visual Studio C++ build tools with Windows SDK, or pass -EmscriptenRoot.' }
$vcvarsPath = Join-Path $installationPath 'VC\Auxiliary\Build\vcvars64.bat'

Push-Location $outputPath
try {
    $compileCommand = 'call "' + $vcvarsPath + '" >nul && cl /nologo /EHsc /std:c++14 /W4 /I "' +
        (Join-Path $projectPath 'src') + '" "' + (Join-Path $projectPath 'src\fall_detector.cpp') +
        '" "' + (Join-Path $PSScriptRoot 'fall_detector_test.cpp') + '" "' +
        (Join-Path $projectPath 'src\fall_alarm.cpp') + '" "' +
        (Join-Path $PSScriptRoot 'fall_alarm_test.cpp') + '" "' +
        (Join-Path $projectPath 'src\push_button.cpp') + '" "' +
        (Join-Path $PSScriptRoot 'push_button_test.cpp') + '" /Fe:fall_detector_test.exe'
    & $env:ComSpec /d /s /c $compileCommand
    if ($LASTEXITCODE -ne 0) { throw 'Host test compilation failed.' }
    & (Join-Path $outputPath 'fall_detector_test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Fall detector tests failed.' }
} finally {
    Pop-Location
}
