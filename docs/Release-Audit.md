# Independent release audit

Date: 25 October 2026, Asia/Kolkata. Version under review: 0.1.1-preview.

**Production release status: BLOCKED. This package is an interface preview.** Earlier completion/readiness claims are superseded by this source-based review. A successful compile or fixture suite does not prove the deployed product works.

## What was actually found

| Area | Evidence and current behavior | Release implication |
|---|---|---|
| Server composition | ServerMain passes only Auth and StaffAuthorizer to VrmsHttpServer; all business ports are null | Business routes return unavailable; service classes existing on disk do not make them connected |
| Booking | BookingView.confirmBtn remains disabled; only local draft validation/review runs | A customer cannot complete a persisted booking in the desktop |
| Records and operations UI | Personnel, drivers, customers, running services, finance and reports contain hardcoded specimen data; actions previously updated labels/tables without HTTP | Reviewed workspace now labels examples and disables unconnected write/export buttons |
| Provider invitation | JdbcStaffProvisioningService previously invented an Auth UUID without creating an Auth user | Corrected to fail explicitly pending a supported invitation provider; no fake membership is inserted |
| Private files | JdbcFileStorageService previously invented a signing token instead of calling Supabase Storage | Corrected to report signing unavailable; no invalid URL is advertised as signed |
| Database context | Operations/finance/automation/reviews/reporting call vrms_private.set_app_context, but no migration defines it | Those adapters require repair and actual restricted-role DB integration before wiring |
| Permissions | Business policies often check only a transaction's company, and finance/compensation depend on role settings; not all operations recheck authoritative membership inside mutation transaction | Active/revoked staff, sensitive fields and concurrent changes need live tests and policy review |
| Allocation | Reservation tables have overlap queries/indexes but no final database exclusion constraint | Multi-connection conflict, freeze/extension/checkout safety needs implementation/verification |
| Financial correctness | Audit/idempotency calls use separate transactions; some writes/postings are not proven atomic | Money/retry/correction acceptance remains pending |
| Worker | Outbox records/scanner exist; no independently running sender/scheduler has been deployed | Laptop-off delivery is not demonstrated |
| Hosted service | User confirms only Supabase is configured | A fresh download has no live Java API to connect to |
| Automated evidence | Stage tests include mock ports, arithmetic on local variables, document-string and file-existence assertions | Earlier journeys, restore/RPO/RTO and full completion claims do not establish live integration |
| Distribution | Windows app-image can be built; macOS/Linux have no native execution evidence | Only Windows preview is publishable; unsigned status is disclosed |

## Corrections made during this review

- Approved Gson now parses/serializes desktop authentication JSON; duplicate keys, invalid UTF-8, excessive depth and responses over 64 KiB fail. Response deadlines cover the complete body. Regex extraction is removed.
- Fixed the real API's missing JSON Content-Type header, discovered when the strict desktop client exercised it.
- Password input is cleared on rejected local validation. Preview navigation is disabled while sign-in is pending. Sign-out now clears its in-memory result and invokes provider logout off the EDT.
- Supabase Session pooler username now includes the project suffix; added configurable CA path and JDBC deadlines. This fix has not been verified against a live operator credential.
- Fixed quote rounding at 24h plus sub-minute/nanosecond boundaries, checked arithmetic overflow, and removed invented default rates for an absent rate plan.
- Fixed the filtered running-service list's selection mapping.
- Unconnected specimen screens are explicitly read-only. They do not record a payment, invite a user, export a PDF or change custody merely by displaying a success label.
- Isolated the packaging/configuration test so it cannot replace the real user's public API endpoint. Saving a new endpoint validates its transport before writing.
- Added semantic-version update checks restricted to the official repository's HTTPS release pages; no executable is downloaded or run automatically.
- Packaging now includes the approved desktop JSON dependency and EC TLS provider, validates recursive-clean paths and excludes server/database artifacts from the client. Runtime/binaries/configuration are ignored by source control.

## Verification boundary

The original 1,534 assertions passed before corrections, despite the disconnected workflows above. A new independent suite checks pricing boundaries/overflow, JSON escaping/duplicate rejection/limits, update URI validation, configuration validation and disabled unconnected mutations. Full revised suite results and native/package evidence are recorded in the repository review report. No public live staff credential, end-to-end booking, payment, private upload, actual mail delivery, database restore or non-Windows device acceptance has been exercised by this review.

## Next work required for a business release

1. Complete and test shared membership-aware transaction boundaries, RLS policies, cross-resource constraints and atomic idempotency/audit/posting behavior.
2. Replace mock UI data/actions with real API adapters/controllers, and wire only tested business services in ServerMain.
3. Complete supported invitation/private-storage integrations and independent email worker setup.
4. Run the original live Admin/Employee journeys and negative/concurrency/recovery tests using fictional data. Update each of the 90 requirement rows only against its original acceptance criterion.
5. Deploy a verified HTTPS API with protected server configuration; configure the desktop's public endpoint. An unauthenticated download does not grant staff membership or access to the company.
6. Build/sign/test the intended platform packages and publish a stable release only after their gates pass.

Publication of a clearly labelled preview and source code is authorized by the user's latest request. This does not change the production blockers. No claim of bug-free operation is made.
