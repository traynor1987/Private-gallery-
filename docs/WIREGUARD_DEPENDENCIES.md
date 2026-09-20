# Production WireGuard dependency audit

Private Gallery 1.0.22 embeds only:

`com.wireguard.android:tunnel:1.0.20230706`

This is the official WireGuard Android tunnel artifact, licensed under Apache-2.0.
It provides the standard `Config` parser and Android `VpnService`-backed
`GoBackend`. Private Gallery supplies no provider integration, server list,
profile, username or private key. Its profile store is app-private and encrypted.

The production dependency/build guard rejects OpenVPN, ics-openvpn, OpenVPN 3,
AGPL and the future fork package from the app and Gradle production paths. The
separate `traynor1987/private-gallery-openvpn2` repository is intentionally not
a submodule or dependency of this APK.
