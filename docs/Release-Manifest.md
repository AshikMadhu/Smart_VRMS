# Smart VRMS release manifest

Version: **0.1.2-preview**. Classification: **authenticated fleet prototype preview**.
Status: **PREVIEW PUBLICATION AUTHORIZED; PRODUCTION BLOCKED** by incomplete business workflows and pending authenticated hosted acceptance. The user-authorized preview release does not establish completed business workflows.

| Artifact | SHA-256 | Bytes |
|---|---|---|
| SmartVRMS-Windows-x64.zip | 7C93EAAE9067130DADD81BB3EA12F7F7B5D95A95BE1FCB65A4CD82F1369C93E3 | 31999142 |
| SmartVRMS.exe | 894192A69CECD4058C36E3A6EB860095276E917EF1068376A1725C0BA76DD4DE | Native Windows launcher |
| server.jar | 86A175419EFEF978750B4C5558E66E5D00454E9F9CD671FF2149147868837345 | Server build input; excluded from the desktop package |

Only the client ZIP and SHA256SUMS.txt are intended as public preview assets. The server JAR is listed to identify local verification inputs; it is not bundled in the client.
These hashes identify the build that generated this file. CI rebuilds publish their own SHA256SUMS.txt and Release-Manifest.md assets; use those release assets to verify the downloaded ZIP, not a different local build's hash.

| Platform | Evidence |
|---|---|
| Windows 11 x64 | App-image build, native keyboard smoke and launcher process test; unsigned |
| macOS Intel / Apple Silicon | PENDING RUNNER — no native device acceptance |
| Ubuntu 24.04 GNOME x64 | PENDING RUNNER — no native device acceptance |

The desktop bundle contains the public API endpoint only. User credentials and session tokens are never packaged. Fleet data is live only after staff authentication; other business workflows remain incomplete. See [Release-Audit.md](Release-Audit.md).
The stable latest-release URL will not resolve until a stable release exists. Preview downloads use their versioned release tags. Already downloaded executables are replaced by downloading a later build.