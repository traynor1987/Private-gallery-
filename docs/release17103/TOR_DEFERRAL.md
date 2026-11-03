# Code55 shipping scope: Tor deferred, VPN release blocked

Owner decision: defer further Tor development/verification, preserve its source and disable its user-facing functionality. Version1.7.10.3/code55, packageuk.co.traynor.privategallery, API37 and existing release branch remain unchanged. No Home redesign, browser-engine replacement, new workflow or unrelated feature is started.

## Implemented deferral

Main-source `NovaReleaseFeatures.TOR_ENABLED=false` prevents preference/intent/build-type activation. The launcher, recent shortcuts, Home search, browser connection choices and Evidence transport choices exclude Tor. Saved Tor destinations fail unavailable, without selecting VPN or direct Internet. An explicit stale Evidence Tor choice remains unavailable until the owner selects another route. Both Tor services are manifest-disabled; startup, bind/establish, native initialization, connection, relay, proxy and HTTP factories reject disabled Tor before networking. Dependency TorService remains removed from the manifest. Original implementation and saved Tor data/catalogues remain in source/storage; nothing is erased or migrated into VPN.

No Tor verification is removed from existing workflows in this checkpoint. Excluding it from a future candidate is conditional on artifact/runtime proof that the disabled configuration is unreachable. Shared VPN/Private Apps security checks remain mandatory.

## DNS investigation: affects shipping VPN continuity

Re-fetched GitHub run41688397704 and its failed private-home1710 job126100834909. Rehashed existing artifact13257432448: archiveSHA256`11f7a4e51e016b3b20be0c657683194c936ad5b2a30d94c628f90eb74f4ceea8`. Raw final captureSHA256`5b1ebb4d21de3a6e32ba0722bedd60fbc923dddd3a936c42ecf8c526cc64f982`; packets/events hashes match the rejected security report.

The complete VPN continuity capture contains two off-WireGuard fixture DNS records and six direct UDP/STUN datagrams, three carrying the exact native-child marker. Root independently re-counted the six actual loopback datagrams, excluding ICMP quoted packets, at18:02:13.069478,18:02:43.029467,18:02:43.220515,18:04:25.303583,18:05:39.451129 and18:06:03.401281 UTC on2026-11-03. The two-site suspension and reconnect/lock/replacement phases are VPN_ONLY. Tor remains on hard retirement in the existing implementation. This is a genuine VPN transition enforcement defect, not a Tor-only fixture failure. No packet exclusion or timing relaxation is permitted.

MainActivity.denyHiddenVpnNetwork still removes WireGuard while the sensitive child survives. Its method body is byte-identical to the failed24c source (SHA256`1b6b3dbe84ee21759b9b588999808d5894fe608b2fecb870f1f139351ffa37e1`). Current WebView pause, proxy/authority revocation and old Network process binding do not enforce no-egress for all native sockets during that removal. Tor deferral does not modify this path. Completed UI/session ownership work and historical8/8 Android passes remain preserved, but cannot supply the missing route containment.

## Verification and release boundary

The launcher availability regression was observed failing before the policy change and passing afterward. All six focused Home/navigation host runners pass. Application and Android test compilation passed locally: `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:processDebugMainManifest`, BUILD SUCCESSFUL in7m37s (32tasks). Merged debug manifest confirms both disabled services and absent dependency TorService; there is no release manifest overlay. Production APK and API37 runtime verification are still pending. The new two-case TorDeferral17103Test is prepared for API37; compilation is not execution.

Independent scoped review closed the Evidence stale-route fallback finding and accepts Tor resource admission at source level. It retains Critical C1 and explicitly withholds release approval. See `TOR_DEFERRAL_SECURITY_REVIEW.md` and the original `CONTINUITY_SECURITY_REVIEW.md`.

Code55 cannot be production-signed until a real enforcement boundary preserves the protective VPN throughout the surviving child's suspension/reconnect/termination, followed by focused native UDP/DNS packet evidence and independent approval. The native persistent-fence foundation already in source is not wired into Android/runtime and is not a fix. Unfinished bridge drafts are preserved separately and were not silently activated by this scope change. No new Android workflow run, production APK,55 checksum, signing activation or original-key54→55 upgrade is claimed. The original production certificate and signed54 baseline remain unchanged.
