# User guide — 0.1.2-preview candidate

Staff sign-in and the fleet category/vehicle screen connect to the hosted company API; hosted sign-in and fleet reading passed operator acceptance. This is not the complete rental system. Booking, payments, returns, personnel, documents, email and reports remain unavailable or fictional examples. See Release-Audit.md for current limitations. The older 0.1.1-preview download does not connect to the hosted API.

## Open the preview

Download the 0.1.2-preview ZIP from the versioned GitHub release, extract the entire archive, and open SmartVRMS/SmartVRMS.exe. Java is bundled. Windows 11 x64 is the tested target; the package is unsigned. The app connects to `https://smart-vrms.onrender.com` by default.

![Staff sign-in preview](screenshots/signin-1280.png)

Choose **Sign in** and use your invited staff account. The first request may take about a minute if the free API has been idle. Do not enter your Supabase database password or publishable key in the sign-in form.

![Rental desk preview](screenshots/overview-1280.png)

## Explore

- Fleet: view company categories and vehicles from the API. Administrator accounts can register categories and vehicles; other staff are read-only according to access policy.
- Other modules: booking, customers/visits, custody, return, personnel, finance and reports are preview examples, not live operations. Their buttons are disabled and their changes do not persist.
- Connection settings: the candidate already has the public service URL. Do not enter a database password or Supabase key there.

## Updates

The app checks for a newer stable GitHub release in the background. Check for updates also runs on demand. When an update exists, confirm opening its official download page, download/extract the new archive, close the old version and launch the new one. The public endpoint is stored outside the installed folder. Staff sessions are memory-only.

No new stable release is available merely because code was pushed. Maintainers must build and publish it. Preview releases are labelled separately and do not appear as the latest stable version.

## Problems

If sign-in fails, confirm the service is awake and retry once. A failed operation must not be treated as a successful save or payment. Report app version, the action, and sanitized error text. Do not include passwords, session tokens, customer identity documents or payment credentials in a public issue.
