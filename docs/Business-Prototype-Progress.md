# Connected business prototype — current development state

The user explicitly requested a usable authenticated business prototype, retaining the original full scope. Preview `0.1.2-preview` is published at https://github.com/AshikMadhu/Smart_VRMS/releases/tag/v0.1.2-preview. SETUP-16 operator acceptance confirmed staff sign-in and a live fleet read from the packaged client. This does not establish that all 90 requirements or the full rental lifecycle are finished; 0.1.1-preview remains a historical interface-only download.

## Live setup facts received

- User-supplied read-only database report initially confirmed migration versions 1–7, 38 application tables, no table without enabled/forced RLS, smart_vrms_api LOGIN with superuser/BYPASSRLS/CREATEROLE/CREATEDB/replication all false; legacy set_app_context(uuid,uuid) function absent. A later screenshot confirmed migration version 8 (migration 009) recorded. Current-source read-only staff authentication, restricted role/TLS, in-transaction membership and fleet read were then reported PASS. Index definitions and business workflows still need verification.
- User reports PASS from scripts/check-live-staff.ps1: actual confirmed staff authentication, authoritative membership, restricted JDBC/TLS and fleet read. This is user-provided live evidence, not an agent-held password or an authenticated business write test.
- Render project URL supplied: https://dashboard.render.com/project/prj-dbf4om67bikc73c69jo0. Connected Render tools still expose the unrelated abhinavaby workspace; the user chose their own account instead. No service was created or modified through that connection. Deployment will use the user's Dashboard until access is corrected.
- User supplied the public service URL `https://smart-vrms.onrender.com`. A read-only GET returned HTTP 200 from `/health`; a GET without credentials to `/api/v1/fleet/vehicles` returned HTTP 401, confirming the public path and protected-route boundary. These checks do not prove staff login or an authenticated company-data read.
- SETUP-16 completed by the user: packaged 0.1.2-preview staff sign-in PASS and fleet screen loaded company rows. This is read acceptance; no live registration/write or broader workflow was performed.

## Implemented now

- JdbcStaffContext validates the restricted login and active company membership/capability within each operation's transaction; it refuses autocommit and does not trust claimed capability strings. Installed across 19 personnel/fleet/driver operations. Stale role or wrong company fails closed; callers preserve authorization errors and roll back.
- Eight existing write/audit pairs moved into one transaction, with Gson-escaped details. Added missing audit records for employee updates, attendance and compensation. Category creation also audits atomically. No successful write is returned before its audit entry succeeds.
- Fleet categories GET/POST routes and JDBC implementation. Vehicle registration validates the category belongs to the authenticated company and returns its real name.
- ServerMain composes the records services, probes the restricted database connection at startup, uses explicit reverse-proxy binding for hosted HTTPS and keeps unconnected booking/operations/finance services unavailable.
- LiveFleetView + FleetHttpClient load real company categories/vehicles and permit administrator category/vehicle creation. Network work is off the EDT, late logout responses are discarded, table cells render plain text, and no mutation is automatically retried after an ambiguous timeout. Other unfinished screens remain clearly labelled read-only examples.
- Memory-only DesktopSession serializes refresh, rejects cross-user/company refresh responses, and prevents a late refresh from undoing logout. Provider logout now explicitly affects only the current session, preserving other devices.
- Safe GET warm-up before credentials handles free-tier wake delays. The verified public API address is now the build default; users will not need to type a backend address in each download.
- Root Dockerfile/.dockerignore for a non-root Java 17 server, complete runtime dependencies and the public provider CA only. A separate GitHub CI container build checks missing-configuration fail-closed behavior. No local software was installed.

## Verification and remaining gate

New focused checks: 63 transaction-context, 20 session/host-binding, 7 atomic fleet-adapter ordering/audit escaping and 7 actual desktop/API/UI protocol checks (fixture repository/Auth for protocol tests). The latter renders verification/generated/live-fleet-1280.png; its layout was inspected and explicit column headers added. The complete revised suite passes **1,654 assertions**. Evidence: docs/evidence/release-review/business-prototype-verification.txt and docs/screenshots/live-fleet-1280.png. This still does not claim a live business write or hosted journey.

Need next: exercise authenticated fleet registration and authorization denial with operator-approved fictional data, then continue with atomic booking, customers/visits, custody/return, financial posting, private files and independent reminders. These remain mandatory and are not silently removed.
