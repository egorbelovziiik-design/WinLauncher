<#
    Build script for STEAL AN EGG ULTIMATE HUB (Windows + .NET 8 SDK).

    Usage:
        powershell -ExecutionPolicy Bypass -File .\build.ps1
        powershell -ExecutionPolicy Bypass -File .\build.ps1 -SkipRestore

    Produces:  dist\StealAnEggUltimate.exe   (self-contained single file, win-x64)
#>
param(
    [switch]$SkipRestore,
    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
$project = Join-Path $PSScriptRoot "StealAnEggUltimate.csproj"
$dist = Join-Path $PSScriptRoot "dist"

Write-Host "=== STEAL AN EGG ULTIMATE HUB — build ===" -ForegroundColor Magenta
Write-Host "Project: $project"

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    throw "The .NET 8 SDK was not found. Install it from https://dotnet.microsoft.com/download/dotnet/8.0 and run this script again."
}

if (-not $SkipRestore) {
    Write-Host "`n[1/3] dotnet restore" -ForegroundColor Cyan
    dotnet restore $project
    if ($LASTEXITCODE -ne 0) { throw "dotnet restore failed." }
}

Write-Host "`n[2/3] dotnet build -c $Configuration" -ForegroundColor Cyan
dotnet build $project -c $Configuration
if ($LASTEXITCODE -ne 0) { throw "dotnet build failed." }

Write-Host "`n[3/3] dotnet publish (win-x64, self-contained, single file)" -ForegroundColor Cyan
dotnet publish $project `
    -c $Configuration `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -p:DebugType=None `
    -p:DebugSymbols=false `
    -o $dist
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed." }

$exe = Join-Path $dist "StealAnEggUltimate.exe"
if (-not (Test-Path $exe)) { throw "EXE was not produced at $exe" }

$item = Get-Item $exe
Write-Host "`nBuild OK" -ForegroundColor Green
Write-Host ("  EXE    : {0}" -f $item.FullName)
Write-Host ("  Size   : {0:N1} MB" -f ($item.Length / 1MB))
Write-Host ("  SHA256 : {0}" -f (Get-FileHash $exe -Algorithm SHA256).Hash)
Write-Host "`nRun it with: $exe"
