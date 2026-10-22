# Code32 focused Hidden browsing repair

Code31 physical acceptance: FAIL. Both modes showed Connected but browser requests produced a synthetic403. Recovered remote baseline c2d2de7a4af1c790bad548a1378005165e762d9b. Installed code31 source dd877ae8869d556bd25aab62d6707213913f679e. CI644 green did not validate real navigation.

Root cause: HiddenHttpTransport rejected every upstream3xx with blocked(), and mapped all transport exceptions/nonGET to the same synthetic403. Startup only fetched example.com200. HTTP onion also lacked production cleartext permission. Owner's onion URL is private/not recovered; no claim that a particular private service was tested.

Repair is confined to Hidden request/navigation handling and code32 signing/upgrade gates. WebView blockNetworkLoads stays true. Native Tor SOCKS remote hostnames and pinned app-owned WireGuard Network/DNS unchanged. Every redirect checked; no direct client or fallback. Main content uses canonical URL, stylesheet redirects use canonical imports. Session/intent fences reject stale callbacks. Safe headers forwarded, native cookies retained only in memory. Distinct local errors; real upstream errors retained. Onion permitted only Tor before DNS, with suffix-scoped HTTP permission; ordinary HTTP clearnet remains denied in release. HTTPS downgrade disallowed. Primary browser code unchanged.

Limits: JavaScript/forms/frames/workers and unsupported schemes remain blocked. Redirected SVG/HTML/XML subresources are unsupported; TLS/client-auth sites or bot-blocked Tor exits may return real errors. Main/CSS redirect canonicalization can issue an extra final GET. VPN+Tor unavailable. No claim of Tor Browser fingerprint protection, complete SameSite semantics or physical DNS-leak verification.

Test-first CI645 reproduced both expected failures. New tests exercise the real production navigation client, native SOCKS fixture and actual WebView, including final document/CSS resource bases; these fixtures do not prove real Tor/VPN routes. Real packaged Tor test additionally attempts BBC HTTPS and published BBC onion, with upstream status and local classification recorded. Full regression/signing/upgrade results pending.

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
