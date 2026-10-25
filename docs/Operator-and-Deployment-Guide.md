# Operator and deployment guide

**Production deployment is blocked.** This release supplies source and a Windows interface preview. Read Release-Audit.md: business service composition, authoritative transaction checks, full desktop API adapters, private Storage, invitations and a running sender/worker remain incomplete.

## Existing development setup

Supabase provides Auth/PostgreSQL; it does not run the Java application API. Public signup and anonymous sign-in are disabled. Application membership is held in private tables, not user-editable Auth metadata. Migration and bootstrap scripts are operator actions for an isolated development project. Never rerun applied migrations or treat application model classes as a verified database upgrade.

The first-admin database setup was user-reported successful. Later migration application and live business acceptance must be verified against actual schema history and restricted-role sessions. Do not run all migrations blindly because this guide exists.

## Server configuration inputs

ServerMain currently supports these existing inputs:

| Input | Purpose |
|---|---|
| SUPABASE_URL | Public provider project URL |
| SUPABASE_PUBLISHABLE_KEY | Publishable provider key |
| SMART_VRMS_DB_HOST / SMART_VRMS_DB_PORT | Session pooler address |
| SMART_VRMS_DB_USER | Restricted pooler username, including project suffix |
| SMART_VRMS_DB_PASSWORD | Server-only restricted role password |
| SMART_VRMS_DB_CA_PATH | Provider root CA file for verify-full TLS |
| SMART_VRMS_PORT or PORT | Local API port |

Do not use the postgres owner login as the runtime. Public properties/CA can be loaded from local-config during development. No secret should be copied into a desktop JAR, release ZIP, Git repository or public log. scripts/setup-runtime-role.ps1 is an explicitly invoked private-console setup; it does not save the role password.

## Local development server

After separately completing required protected configuration:

```powershell
. .\scripts\server-classpath.ps1
java -cp $serverClassPath in.smartvrms.server.ServerMain
```

It currently binds loopback and composes authentication only. A health response is liveness, not proof of a working rental workflow or database connection. Business routes without service ports remain unavailable. Do not bypass that condition with owner credentials or broad grants.

## Before public hosting

1. Complete the integration and database/permission defects listed in Release-Audit.md.
2. Run the original Admin and Employee acceptance journeys against fictional data using the real restricted runtime role, including revocation, overlapping bookings, partial returns, money reconciliation and private-file denial.
3. Select and verify a currently available zero-recurring-cost Java API and independent scheduler/sender topology. Document sleep/pause/quota behavior. A sleeping free web API is not an independently running worker.
4. Prepare the actual container/bind/TLS/CA/secret/health/shutdown configuration for that host, and obtain required account setup. No provider deployment occurred in the reviewed release.
5. Perform real laptop-off reminder delivery, backup and restore drills, then record evidence and open the deployment gate.
6. Build the desktop with the verified public API endpoint and test it on a separate supported machine before a stable release.

scripts/export-server-bundle.ps1 creates a local unverified container bundle; exporting it does not make hosting operational. Its generated deployment examples are not a substitute for the host-specific review above.

## Publication

Source and the explicitly labelled preview may be published under the user's current request. Stable production and untested platform claims remain blocked. Keep generated runtimes/binaries out of source history; upload the checksummed client ZIP as a GitHub Release asset. Publishing new builds updates the release links; already downloaded files require a user-accepted replacement download.
