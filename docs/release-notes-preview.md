# Smart VRMS 0.1.1-preview

**Read-only interface preview — business integration incomplete.**

Download `SmartVRMS-Windows-x64.zip`, extract the entire archive, and open `SmartVRMS/SmartVRMS.exe`. Windows 11 x64; Java is bundled. The package is unsigned. `SHA256SUMS.txt` identifies the published archive.

Use the checksum and Release-Manifest.md attached to this release, since a build on another machine can have different bytes/runtime metadata.

The independent review fixed strict JSON/HTTP handling, a missing API JSON Content-Type, 24-hour pricing boundary and overflow errors, a filtered-list selection error, test configuration pollution and misleading local success actions. The app now labels fictional examples and disables unconnected operations. It includes an official GitHub stable-release update checker.

There is no hosted Java API. Booking confirmation, real staff/driver/customer edits, custody, payments, private files, email and reports are not connected end to end. The download must not be used to record real rentals or money. A source adapter or a passing fixture test is not proof of a working business feature.

See [the release audit](https://github.com/AshikMadhu/Smart_VRMS/blob/main/docs/Release-Audit.md) for remaining requirements and evidence. macOS/Linux native packages remain untested and are not supplied.

Future source changes need a new versioned build/release. Already downloaded files stay unchanged; **Check for updates** notifies users about a future stable release and opens its official page. No update is installed automatically.
