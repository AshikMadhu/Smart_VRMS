<div align="center">

# Smart VRMS
### A native workspace for vehicle rental operations

![Java 17](https://img.shields.io/badge/Java-17-2b5b68?style=flat-square)
![Desktop](https://img.shields.io/badge/Desktop-Swing-2b5b68?style=flat-square)
![Windows](https://img.shields.io/badge/Preview-Windows%2011%20x64-2b5b68?style=flat-square)
![Status](https://img.shields.io/badge/Status-Interface%20preview-d08b36?style=flat-square)

[Download Windows preview](https://github.com/AshikMadhu/Smart_VRMS/releases/tag/v0.1.1-preview) · [Release audit](docs/Release-Audit.md) · [Requirements](planning/Requirements-and-Verification.csv)

</div>

![Smart VRMS rental desk — fictional preview](docs/screenshots/overview-1280.png)

Smart VRMS is a Java 17 and Swing project for a rental company's fleet, people, customers and rental lifecycle. It uses a Java API with Supabase Auth and PostgreSQL, with company records and permissions enforced on the server.

> **Current release: 0.1.1-preview.** This download is a read-only interface preview, not a finished rental management system. There is no hosted Java API yet. Sample data is fictional; the booking, payment, personnel, document and reporting screens are not connected end to end. Read the [independent release audit](docs/Release-Audit.md) before using it for an assessment or planning business use.

## Download and open

1. Open the [preview release](https://github.com/AshikMadhu/Smart_VRMS/releases/tag/v0.1.1-preview).
2. Download **SmartVRMS-Windows-x64.zip** and optionally **SHA256SUMS.txt**.
3. Extract the **entire archive**; open `SmartVRMS/SmartVRMS.exe`.
4. Choose **Explore interface preview**. Java is bundled; no separate installation is needed.

Windows 11 x64 is the locally tested platform. The build is unsigned. macOS and Linux scripts are present, but packages have not been built and tested on those systems. Staff sign-in needs an operator-configured API and an invited account; downloading the app does not create an account or grant access to company records.

## What works in this build

| Area | Current behavior |
|---|---|
| Native interface | Grouped navigation, gallery/detail examples, resizing and keyboard navigation |
| Rental draft | Local required-field/date validation, vehicle shortlist, review and edit |
| Authentication code | Strict bounded JSON/HTTP client, Supabase session adapter, JDBC membership authorization; live hosted journey still pending |
| Preview modules | Read-only fictional personnel, customer, driver, custody, finance and report layouts |
| Updates | Background notice and **Check for updates** link to a newer official stable release |
| Windows distribution | Portable native launcher, bundled runtime, checksummed ZIP |

The approved scope also includes real fleet/document history, attendance/compensation, atomic package bookings, driver eligibility, extensions, inspections/damage, immutable finance, PDFs, UPI/manual reconciliation, private storage, email/reminders, reviews and role-specific reports. These are tracked against **90 requirements**. Their source models, adapters and migrations are not proof of complete integration. The [audit](docs/Release-Audit.md) lists the unfinished connections and safety gates.

## Updates

A downloaded executable is a fixed version. Source changes appear in downloads only after a new build is published.

- The app checks GitHub in the background without sending credentials. When a newer stable version exists, **Update available — download** opens its release page with your approval.
- Download and extract the new archive, close the previous version, then open the new executable. Public API configuration stays in your user profile; session tokens are memory-only.
- The permanent stable-release download format is [`releases/latest/download/SmartVRMS-Windows-x64.zip`](https://github.com/AshikMadhu/Smart_VRMS/releases/latest/download/SmartVRMS-Windows-x64.zip). **It becomes available when the first stable release is published.** GitHub excludes prereleases from `latest`.
- Current preview builds remain under their explicitly labelled release tags. Update checks never install or execute downloaded code automatically.

## Architecture

```text
Swing desktop → authenticated Java API → Supabase Auth / PostgreSQL / private Storage
                      │
                      └─ restricted transactions, business rules and audit
```

| Module | Responsibility |
|---|---|
| `contracts` | Java records and shared data contracts |
| `application` | Ports and application validation |
| `desktop` | Swing views/controllers, HTTP integration and public configuration |
| `server` | Auth verification, JDBC services, pricing, finance and API routes |

Server code and database credentials are excluded from the desktop archive. The reviewed client uses the approved Gson dependency for JSON; PostgreSQL/PDF/QR libraries remain on the server. Dependency details are in [third-party notices](docs/Third-Party-Notices.md).

## Build and verify

Requires JDK 17 and Maven. Commands below use Windows PowerShell; no machine execution-policy change is needed.

```powershell
git clone https://github.com/AshikMadhu/Smart_VRMS.git
Set-Location Smart_VRMS
mvn -B package          # Resolves the project's pinned dependencies on first build
.\scripts\verify.ps1   # Offline reactor plus local Java/HTTP/Swing checks
.\scripts\run-preview.ps1
```

The full legacy distribution checks require a generated package/server bundle and current manifest. For a fresh source checkout, follow the [developer guide](docs/Developer-Guide.md). Regression evidence distinguishes fixture tests, native UI checks and live integrations. A green suite does not certify all mandatory workflows.

To rebuild the Windows preview:

```powershell
.\scripts\package-desktop-windows.ps1
```

Generated binaries and runtimes go under `dist/` and are excluded from Git. Release assets belong in GitHub Releases, not source commits.

## Configuration and security

Use **Connection settings** to save the public HTTPS URL of the Java API, then restart. The Supabase project URL is not the Java API URL. Remote connections require HTTPS; explicit `127.0.0.1` HTTP is supported for local development only.

Never put database passwords, Supabase secret/service-role keys, access tokens or refresh tokens in Git, screenshots, issue comments or desktop configuration. Server configuration is separate. Public signup remains disabled; administrators control staff access.

## Documentation

- [Release audit and remaining blockers](docs/Release-Audit.md)
- [Release manifest and checksums](docs/Release-Manifest.md)
- [User guide](docs/User-Guide.md)
- [Developer guide](docs/Developer-Guide.md)
- [Operator and deployment guide](docs/Operator-and-Deployment-Guide.md)
- [Delivery checklist](docs/Delivery-Checklist.csv) and [approved baseline](docs/Baseline-Approval.md)

Source availability does not imply an open-source license. Third-party software retains its own licenses and notices.
