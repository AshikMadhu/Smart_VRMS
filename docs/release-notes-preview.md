# Smart VRMS 0.2.0-preview

**Authenticated rental-management beta preview. This is not a production-certified or bug-free system.**

Download `SmartVRMS-Windows-x64.zip`, extract the full archive, and open `SmartVRMS/SmartVRMS.exe`. Windows x64 package; Java is bundled. The binary is unsigned. Use the `SHA256SUMS.txt` and `Release-Manifest.md` attached to this exact release.

The current source includes the implemented rental-management modules and passes the repository's local fixture/regression verification suite. Operator acceptance on the hosted development system covers staff sign-in and fleet reads, plus Supabase migration 009 and its six indexes. Authenticated live writes and complete end-to-end business journeys have not been independently accepted against the hosted database. Validate with authorized staff and approved test records before using real rentals, financial records, or customer data.

The application uses the public API at `https://smart-vrms.onrender.com` by default. Staff accounts and company membership must already be provisioned by an administrator; downloading the client does not grant access. The prototype uses manual UPI/payment recording, not automatic payment confirmation. Statutory payroll, outbound email delivery, and verified backup/restore are not claimed as operational. Free hosting can sleep or become unavailable.

macOS and Linux packages are not supplied. This Windows package is unsigned. Already downloaded copies do not update automatically; Check for updates opens the release page and the user downloads the new version.
