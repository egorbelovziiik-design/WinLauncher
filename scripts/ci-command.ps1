param([Parameter(Mandatory=$true)][string]$Command, [Parameter(Mandatory=$true)][string]$LogName)
# Preserve the real native exit status while keeping a complete log. Progress
# notices are readable through api.github.com even when Actions blob hosts are
# inaccessible from the development sandbox.
$PSNativeCommandUseErrorActionPreference = $false
& cmd.exe /d /s /c "$Command 2>&1" | Tee-Object -FilePath $LogName | ForEach-Object {
  $line = [string]$_
  Write-Output $line
  if ($line -match '^(PASSED: |FAILED: |test result: )') {
    $safe = $line.Replace('%','%25').Replace("`r",'%0D').Replace("`n",'%0A')
    Write-Output "::notice title=NATIVE_ACCEPTANCE_PROGRESS::$safe"
  }
}
$code = $LASTEXITCODE
if ($code -ne 0) {
  $tail = (Get-Content -LiteralPath $LogName -Tail 180) -join "`n"
  if ($tail.Length -gt 48000) { $tail = $tail.Substring($tail.Length - 48000) }
  $escaped = $tail.Replace('%','%25').Replace("`r",'%0D').Replace("`n",'%0A')
  Write-Output "::error title=Native compiler or test diagnostics::$escaped"
}
exit $code
