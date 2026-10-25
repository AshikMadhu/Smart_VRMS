# Smart VRMS release manifest

Version: **0.1.1-preview**. Classification: **read-only interface preview**.
Status: **PREVIEW PUBLICATION AUTHORIZED; PRODUCTION BLOCKED** by the independent release audit.
The user explicitly requested pushing source and publishing a download. This authorizes preview publication; it does not establish completed business workflows.

| Artifact | SHA-256 | Bytes |
|---|---|---|
| SmartVRMS-Windows-x64.zip | 17680FA285BE2495616DD5D7F5E42E2C33FF07F12911B309625FF2A4DD16D7CA | 31979521 |
| SmartVRMS.exe | 3EA489C2FDC019E66CD14B33027E400EE88C8EE7F8DA7CA93FC4F55E48D8F319 | Native Windows launcher |
| server.jar | 65C878366A69D67D95626C570BD5F7AC159B234FF4A1EB057362F99BF09EE741 | Local server bundle; not a deployed API |

Only the client ZIP and SHA256SUMS.txt are intended as public preview assets. The server JAR is listed to identify local verification inputs; it is not bundled in the client.

| Platform | Evidence |
|---|---|
| Windows 11 x64 | App-image build, native keyboard smoke and launcher process test; unsigned |
| macOS Intel / Apple Silicon | PENDING RUNNER — no native device acceptance |
| Ubuntu 24.04 GNOME x64 | PENDING RUNNER — no native device acceptance |

Current source includes strict desktop JSON, pricing boundary/overflow corrections, disabled unconnected specimen actions and an official GitHub stable-release update check.
No backend is deployed. Users can explore the interface; real booking, payment, personnel, storage, email and reporting journeys remain incomplete. See [Release-Audit.md](Release-Audit.md).
The stable latest-release URL will not resolve until a stable release exists. Preview downloads use their versioned release tags. Already downloaded executables are replaced by downloading a later build.