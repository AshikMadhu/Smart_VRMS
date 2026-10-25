# Smart VRMS 0.1.2-preview

**Authenticated fleet prototype preview — remaining business workflows are incomplete.**

Download `SmartVRMS-Windows-x64.zip`, extract the entire archive, and open `SmartVRMS/SmartVRMS.exe`. Windows 11 x64; Java is bundled. The package is unsigned. `SHA256SUMS.txt` identifies the published archive.

Use the checksum and Release-Manifest.md attached to this release, since a build on another machine can have different bytes/runtime metadata.

Staff sign-in connects to the hosted Java API. The fleet screen reads real company categories and vehicles; administrators can register fleet categories and vehicles. The package contains only public endpoint configuration and no database password, API secret or user session. Other business screens remain fictional examples and unconnected actions stay disabled.

Booking confirmation, staff/driver/customer management, custody, returns, payments, private files, email and reports are not connected end to end. Do not use those areas to record rentals or money. A source adapter or a passing fixture test is not proof of a working business feature.

See [the release audit](https://github.com/AshikMadhu/Smart_VRMS/blob/main/docs/Release-Audit.md) for remaining requirements and evidence. macOS/Linux native packages remain untested and are not supplied.

Future source changes need a new versioned build/release. Already downloaded files stay unchanged; **Check for updates** notifies users about a future stable release and opens its official page. No update is installed automatically.
