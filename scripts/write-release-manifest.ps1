$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$archivePath = 'dist/SmartVRMS-Windows-x64.zip'
$archiveHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
$exeHash = (Get-FileHash -LiteralPath 'dist/package-win64/SmartVRMS/SmartVRMS.exe' -Algorithm SHA256).Hash
$serverHash = (Get-FileHash -LiteralPath 'dist/server-bundle/server.jar' -Algorithm SHA256).Hash
$archiveBytes = (Get-Item -LiteralPath $archivePath).Length
$manifest = @"
# Smart VRMS release manifest

Version: **0.1.1-preview**. Classification: **read-only interface preview**.
Status: **PREVIEW PUBLICATION AUTHORIZED; PRODUCTION BLOCKED** by the independent release audit.
The user explicitly requested pushing source and publishing a download. This authorizes preview publication; it does not establish completed business workflows.

| Artifact | SHA-256 | Bytes |
|---|---|---|
| SmartVRMS-Windows-x64.zip | $archiveHash | $archiveBytes |
| SmartVRMS.exe | $exeHash | Native Windows launcher |
| server.jar | $serverHash | Local server bundle; not a deployed API |

Only the client ZIP and SHA256SUMS.txt are intended as public preview assets. The server JAR is listed to identify local verification inputs; it is not bundled in the client.

| Platform | Evidence |
|---|---|
| Windows 11 x64 | App-image build, native keyboard smoke and launcher process test; unsigned |
| macOS Intel / Apple Silicon | PENDING RUNNER — no native device acceptance |
| Ubuntu 24.04 GNOME x64 | PENDING RUNNER — no native device acceptance |

Current source includes strict desktop JSON, pricing boundary/overflow corrections, disabled unconnected specimen actions and an official GitHub stable-release update check.
No backend is deployed. Users can explore the interface; real booking, payment, personnel, storage, email and reporting journeys remain incomplete. See [Release-Audit.md](Release-Audit.md).
The stable latest-release URL will not resolve until a stable release exists. Preview downloads use their versioned release tags. Already downloaded executables are replaced by downloading a later build.
"@
[IO.File]::WriteAllText((Join-Path (Get-Location) 'docs/Release-Manifest.md'),$manifest,(New-Object Text.UTF8Encoding($false)))
Write-Output 'Release manifest written from current artifact hashes.'
