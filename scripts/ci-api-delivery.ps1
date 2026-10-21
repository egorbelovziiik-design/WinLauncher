param([Parameter(Mandatory=$true)][string[]]$Files)
# Immutable blobs only: no Git tree, commit, tag or branch mutation. Actions
# artifacts remain the normal download route. The single manifest notice avoids
# GitHub's per-step annotation limit silently dropping later delivery records.
$ErrorActionPreference = 'Stop'
$records = [System.Collections.Generic.List[object]]::new()
function Publish-Blob([byte[]]$Bytes) {
  if ($Bytes.Length -gt 95000000) { throw 'Delivery file exceeds GitHub blob API size limit' }
  $payload = @{ encoding = 'base64'; content = [Convert]::ToBase64String($Bytes) } | ConvertTo-Json -Compress
  $inputFile = Join-Path $env:RUNNER_TEMP ('winlauncher-delivery-' + [Guid]::NewGuid().ToString() + '.json')
  [System.IO.File]::WriteAllText($inputFile, $payload, [System.Text.UTF8Encoding]::new($false))
  try {
    $response = & gh api --method POST "repos/$env:GITHUB_REPOSITORY/git/blobs" --input $inputFile
    if ($LASTEXITCODE -ne 0) { throw 'GitHub API blob delivery failed' }
    return ($response | ConvertFrom-Json).sha
  } finally { Remove-Item -LiteralPath $inputFile -Force }
}
foreach ($file in $Files) {
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
  $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $file))
  $sha = Publish-Blob $bytes
  $digest = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
  $records.Add(@{ name = (Split-Path $file -Leaf); blob = $sha; sha256 = $digest; size = $bytes.Length })
}
$index = @{ source_commit = $env:GITHUB_SHA; files = $records.ToArray() } | ConvertTo-Json -Depth 5 -Compress
$indexBytes = [System.Text.Encoding]::UTF8.GetBytes($index)
$indexBlob = Publish-Blob $indexBytes
$indexHash = [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($indexBytes)).ToLower()
Write-Output "::notice title=API_DELIVERY_INDEX::delivery-index.json|$indexBlob|$indexHash|$($indexBytes.Length)"
