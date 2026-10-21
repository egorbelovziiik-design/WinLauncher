param([Parameter(Mandatory=$true)][string]$Command, [Parameter(Mandatory=$true)][string]$LogName)
# Capture compiler diagnostics as check annotations as well as normal logs.
# This lets restricted environments read errors through api.github.com without
# accessing the separate Actions blob storage hosts.
& cmd.exe /d /s /c "$Command > $LogName 2>&1"
$code = $LASTEXITCODE
Get-Content -LiteralPath $LogName | Write-Output
if ($code -ne 0) {
  $tail = (Get-Content -LiteralPath $LogName -Tail 180) -join "`n"
  if ($tail.Length -gt 48000) { $tail = $tail.Substring($tail.Length - 48000) }
  $escaped = $tail.Replace('%','%25').Replace("`r",'%0D').Replace("`n",'%0A')
  Write-Output "::error title=Native compiler or test diagnostics::$escaped"
}
exit $code
