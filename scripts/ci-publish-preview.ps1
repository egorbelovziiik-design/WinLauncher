# Explicit opt-in publication of THIS native build. Do not replace unrelated
# releases or move their tags. Publication occurs only after actual checks.
$ErrorActionPreference = 'Stop'
$version = (Get-Content package.json -Raw | ConvertFrom-Json).version
$tag = "winlauncher-native-v$version-preview.1"
$reports = @{}
foreach ($name in @('BACKEND-SMOKE', 'INSTALLER-SMOKE', 'NATIVE-SMOKE')) {
  $file = "release-artifacts/$name.json"
  if (-not (Test-Path $file)) { throw "Missing acceptance report: $name" }
  $report = Get-Content -Raw $file | ConvertFrom-Json
  if ($report.source_commit -ne $env:GITHUB_SHA) { throw "Wrong report source commit: $name" }
  if (@($report.checks | Where-Object status -eq 'FAILED').Count -gt 0) { throw "Failed acceptance checks: $name" }
  $reports[$name] = $report
}
foreach ($check in @('Create and persist a real local offline account in SQLite', 'Offline launch identity resolves without Microsoft tokens or Client ID', 'Delete an offline account without touching Microsoft credentials')) {
  if (-not ($reports['BACKEND-SMOKE'].checks | Where-Object { $_.check -eq $check -and $_.status -eq 'PASSED' })) { throw "Unverified offline-account scenario: $check" }
}
foreach ($versionUnderTest in @('1.21.4','26.2','1.16.5')) {
  $expected = "Real Fabric $versionUnderTest JVM reaches Minecraft client initialization"
  if (-not ($reports['BACKEND-SMOKE'].checks | Where-Object { $_.check -eq $expected -and $_.status -eq 'PASSED' })) { throw "Missing real Fabric bootstrap result: $versionUnderTest" }
}
if (@($reports['INSTALLER-SMOKE'].checks | Where-Object status -eq 'PASSED').Count -lt 2) { throw 'Installer installation/uninstallation not verified' }
$files = @('WinLauncher.exe', 'WinLauncher-Setup.exe') | ForEach-Object {
  $file = Get-Item "release-artifacts/$_"
  @{ name = $file.Name; size = $file.Length; sha256 = (Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLower() }
}
@{ source_commit = $env:GITHUB_SHA; tag = $tag; files = $files; build_url = "https://github.com/$env:GITHUB_REPOSITORY/actions/runs/$env:GITHUB_RUN_ID"; unsigned = $true } | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 release-artifacts/BUILD-METADATA.json
$notes = Get-Content docs/RELEASE-NOTES.native.md -Raw
$notes += "`n`nSource commit: ``$env:GITHUB_SHA``.`nBuild: https://github.com/$env:GITHUB_REPOSITORY/actions/runs/$env:GITHUB_RUN_ID`n"
Set-Content -Encoding utf8 release-artifacts/RELEASE-NOTES.md $notes
$summary = "# Current build verification`n`nSource commit: $env:GITHUB_SHA`n`nOnly the statuses below apply to this build. Native GUI inspection and rendered gameplay are not inferred from compilation or process liveness.`n`n"
foreach ($name in @('BACKEND-SMOKE','INSTALLER-SMOKE','NATIVE-SMOKE')) {
  $summary += "## $name`n`n"
  foreach ($check in $reports[$name].checks) { $summary += "### $($check.check): $($check.status)`n`n$($check.detail)`n`n" }
}
Set-Content -Encoding utf8 release-artifacts/TESTING.md $summary
Copy-Item docs/QUICKSTART-1.0.1.ru.txt release-artifacts/START-HERE-RU.txt
$assets = @('release-artifacts/WinLauncher.exe','release-artifacts/WinLauncher-Setup.exe','release-artifacts/SHA256SUMS.txt','release-artifacts/BUILD-METADATA.json','release-artifacts/TESTING.md','release-artifacts/START-HERE-RU.txt','release-artifacts/BACKEND-SMOKE.json','release-artifacts/INSTALLER-SMOKE.json','release-artifacts/NATIVE-SMOKE.json')
# A failed/partial publication stays a draft. An existing tag causes a clear
# failure rather than overwriting an existing release or executable.
& gh release create $tag @assets --repo $env:GITHUB_REPOSITORY --target $env:GITHUB_SHA --title "WinLauncher Native $version preview — Fabric, Java и версии Minecraft" --notes-file release-artifacts/RELEASE-NOTES.md --draft --prerelease
if ($LASTEXITCODE -ne 0) { throw 'Could not create/upload the new native preview release; existing releases were not replaced' }
& gh release edit $tag --repo $env:GITHUB_REPOSITORY --draft=false --prerelease
if ($LASTEXITCODE -ne 0) { throw 'The native release is still a draft; publication failed' }
Write-Output "::notice title=DIRECT_EXE::https://github.com/$env:GITHUB_REPOSITORY/releases/download/$tag/WinLauncher.exe"
Write-Output "::notice title=DIRECT_SETUP::https://github.com/$env:GITHUB_REPOSITORY/releases/download/$tag/WinLauncher-Setup.exe"
