# Code32 focused Hidden browsing repair

Code31 physical acceptance: FAIL. Both modes showed Connected but browser requests produced a synthetic403. Recovered remote baseline c2d2de7a4af1c790bad548a1378005165e762d9b. Installed code31 source dd877ae8869d556bd25aab62d6707213913f679e. CI644 green did not validate real navigation.

Root cause: HiddenHttpTransport rejected every upstream3xx with blocked(), and mapped all transport exceptions/nonGET to the same synthetic403. Startup only fetched example.com200. HTTP onion also lacked production cleartext permission. Owner's onion URL is private/not recovered; no claim that a particular private service was tested.

Repair is confined to Hidden request/navigation handling and code32 signing/upgrade gates. WebView blockNetworkLoads stays true. Native Tor SOCKS remote hostnames and pinned app-owned WireGuard Network/DNS unchanged. Every redirect checked; no direct client or fallback. Main content uses canonical URL, stylesheet redirects use canonical imports. Session/intent fences reject stale callbacks. Safe headers forwarded, native cookies retained only in memory. Distinct local errors; real upstream errors retained. Onion permitted only Tor before DNS, with suffix-scoped HTTP permission; ordinary HTTP clearnet remains denied in release. HTTPS downgrade disallowed. Primary browser code unchanged.

Limits: JavaScript/forms/frames/workers and unsupported schemes remain blocked. Redirected SVG/HTML/XML subresources are unsupported; TLS/client-auth sites or bot-blocked Tor exits may return real errors. Main/CSS redirect canonicalization can issue an extra final GET. VPN+Tor unavailable. No claim of Tor Browser fingerprint protection, complete SameSite semantics or physical DNS-leak verification.

Test-first CI645 reproduced both expected failures. New tests exercise the real production navigation client, native SOCKS fixture and actual WebView, including final document/CSS resource bases; these fixtures do not prove real Tor/VPN routes. Real packaged Tor tests additionally request BBC HTTPS and a controlled real v3 onion service, with upstream status, local classification and exact random onion response asserted. CI649 final results are recorded below.

## Physical acceptance

Install the identified original-key code32 APK over code31. Never uninstall or clear data. Use disposable sites/media for investigation.

- [ ] Tor Only loads ordinary HTTPS websites.
- [ ] Tor Only loads a valid onion service.
- [ ] VPN Only loads ordinary HTTPS websites.
- [ ] No incorrect local403; website errors and local failures distinguished.
- [ ] Safe relative/cross-host redirects and relative page/CSS assets work.
- [ ] Drop Tor/WireGuard/DNS/network; no direct fallback. Retry works.
- [ ] Background/lock/mode switching/process restart revoke old requests.
- [ ] Primary WireGuard browser still works.
- [ ] Existing encrypted media/PINs/collections remain intact.
- [ ] Hidden and Primary browsing data remain isolated.
- [ ] Signed APK upgrades over code31 without data loss.
- [ ] Physical gateway/WireGuard packet and DNS capture verifies both routes and failure/lock behaviour.

Physical acceptance remains mandatory before production GO. No main merge without owner approval.

## Network gate investigation

CI646 focused46/46 passed, then full267/268 passed; public BBC HTTP onion failed in the full suite. CI647 attempt1 Tor bootstrap90s failed (45/46 other preflight passed); unchanged attempt2 bootstrapped and BBC HTTPS returned200 but HTTP onion returned local503 transport. These failures are retained. The public onion test used HTTP whereas Tor Project publishes HTTPS; it is replaced in CI by a temporary controlled real v3 onion service with descriptor publication observed and an exact random response asserted through the packaged Android Tor client. This is real network testing, not a mocked onion result. The local fallback public endpoint uses published HTTPS. No live WireGuard profile or physical routing capture is available. No test is skipped or changed to manufacture a successful onion response.

## Final verified result

CI649 SUCCESS, signed source `7765c79874556e8093612b64eeb529e095c9c287`.333 JVM,46 focused Android,268 full Android tests: zero failures/errors/skips (focused overlaps full). Actual production WebView/SOCKS fixture tests pass; real native Tor BBC HTTPS200 and controlled onion200/exact81-byte response pass in both suites. Separate profile process seed/verify and debug/production original-key synthetic code31→32 upgrades pass, including restart and encrypted Primary/Hidden data/PIN preservation. Debug/release lint, compilation, secret gate and native inventory pass. Earlier645 expected red reproduction,646/647 public onion/network failures and648 bootstrap failure remain in recovery history.

Original certificate `94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17`; package `uk.co.traynor.privategallery`, version32/name1.6, not debuggable. APK `Private-Gallery-1.6-HTTP403-code32-7765c79.apk`,61,127,817 bytes, SHA256 `b2d7a1a584bbc255659a55d844ba7a34ea60faa65c2acaeb8fc8b7e29c0743f3`. Downloaded ZIP hashes independently matched GitHub artifact metadata and extracted APK matched production evidence. No replacement signing key.

Source review corrected CSS redirect-base/error-status and stale-navigation issues; follow-up found no remaining critical/important source-confirmed blocker. WebView provides no unique navigation ID for indistinguishable delayed same-URL callbacks; no perfect provenance claim. Startup test observes Tor bootstrap for the existing120s budget, not complete production lifecycle clock equivalence.

GO for controlled owner acceptance only. Production NO-GO pending the physical checklist, real WireGuard routing, DNS-leak/failure captures and Fold owner confirmation. VPN+Tor unavailable; restricted GET renderer keeps scripts/forms/frames/workers blocked. Cold direct Hidden VPN requires an already approved loaded profile. Primary browser and vault formats unchanged. Evidence: `evidence-ci649/`. No main merge.
