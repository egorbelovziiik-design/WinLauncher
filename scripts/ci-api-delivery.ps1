param([Parameter(Mandatory=$true)][string[]]$Files)
# Optional delivery bridge for this development sandbox: Actions artifacts use
# a separate blob-storage host that the sandbox cannot reach. Store immutable
# GitHub API blobs WITHOUT a tree, commit, tag or branch change. The build is also
# uploaded normally as an Actions artifact. No generated binaries enter Git history.
# The notice contains only filenames, public content hashes and Git blob IDs.
$ErrorActionPreference = 'Stop'
foreach ($file in $Files) {
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
  $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $file))
  if ($bytes.Length -gt 95000000) { throw 'Delivery file exceeds GitHub blob API size limit' }
  $payload = @{ encoding = 'base64'; content = [Convert]::ToBase64String($bytes) } | ConvertTo-Json -Compress
  $inputFile = Join-Path $env:RUNNER_TEMP ('winlauncher-delivery-' + [Guid]::NewGuid().ToString() + '.json')
  [System.IO.File]::WriteAllText($inputFile, $payload, [System.Text.UTF8Encoding]::new($false))
  try {
    $response = & gh api --method POST "repos/$env:GITHUB_REPOSITORY/git/blobs" --input $inputFile
    if ($LASTEXITCODE -ne 0) { throw "GitHub API blob delivery failed: $file" }
    $blob = $response | ConvertFrom-Json
    $digest = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
    $name = Split-Path $file -Leaf
    Write-Output "::notice title=API_DELIVERY::$name|$($blob.sha)|$digest|$($bytes.Length)"
  } finally {
    Remove-Item -LiteralPath $inputFile -Force
  }
}
