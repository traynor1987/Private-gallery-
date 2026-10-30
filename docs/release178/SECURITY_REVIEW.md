# Private Gallery 1.7.8 consolidated independent security review

**Disposition: APPROVED for the exact reviewed source and controlled original-key signing/upgrade stage.** No unresolved Critical or Important source/runtime finding remains after the corrections and independent evidence validation below. This report supersedes the historical review of 795bbe9617a49b53192b9db76a448f35d344c1d8; it does not assert a completed signed upgrade, approve delivery before the mandatory original-key upgrade/restart gate, or replace physical owner acceptance.

## Reviewed source

Candidate 04b91ae01c64323aac02975672d6046dfba62e30, tree a38946a5e1d58d5cfbde825d041fb426ac743c60, version 1.7.8/code 47. Whole production comparison parent 64d4c213fe3b3a92ecd946e334e8a918851636c9; frozen original1.7.7 application 25aae6d59f79c455e47acc73a5c99f9c8f15242a. The reviewer inspected the complete affected production architecture and subsequent corrective deltas, including existing authentication, encryption, transport, process retirement and descriptor dependencies. No production source was edited by this reviewer.

Current run 41074139917/job 124125688565, attempt 1, completed SUCCESS. Independent API checks confirm exact candidate/tree, push on release/1.7.8-private-apps, .github/workflows/fast-development.yml, successful host/compile/JVM/focusedAndroid steps and broad historical job skipped. Previous failed aggregates remain failed evidence; they are not promoted to success.

## Consolidated source findings and corrections

**Catalogue and authority.** Primary encrypted catalogue handles capture original key identity, authentication epoch and transfer/presentation generations. Key copies are per operation and wiped; staged ciphertext promotion uses the revocation gate. Hidden keeps keys in the parent and authenticated HiddenSession/broker commit authority. Bounded separate records distinguish Primary, HiddenVPN and HiddenTor. Corruption is refused; definitions contain no credential API. Saved IDs are resolved under originating authority before launch. UI clears on authority loss and rejects stale callbacks.

**Authentication/presentation.** Fresh PIN grants one unchanged saved-entry launch; newer tickets supersede older ones. Explicit Main.immediate confines native presentation checks and completion to Main; derivation remains IO and input buffers are wiped. Same-URL reload/navigation revision fences remain. This protects opening the saved entry, not direct navigation or a per-site account boundary. App chrome uses existing tabs/WebViews/transports; launch-time metadata can remain on an open tab after catalogue changes.

**Sessions.** Primary apps/tabs share their existing default profile; retention/clearing explicitly names that whole scope. Complete asynchronous WebStorage/cookie clearing uses durable pending state and a generation barrier before networking/new rendering; startup also retries configured clear-on-lock. Failed completion stays gated. Hidden tabs share a temporary profile retired on closure/revocation; encrypted saved definitions do not retain Hidden login cookies. No per-app cookie isolation or persistent Hidden-login claim is made.

**Metadata/icons.** Public metadata is same-origin, bounded and unauthenticated: no cookies/origin Authorization, redirects, ambient cache or direct fallback. Manifest start/scope/display support is limited and accurately described; it is not a browser sandbox/install/offline/push guarantee. Raster dimensions/bytes precede decode; normalized small PNGs persist encrypted, selected source URI does not. Hidden icon picker uses authenticated in-vault previews without upload grants. Temporary byte buffers are wiped and bitmaps recycled; no plaintext icon disk cache. Editor cancellation suppresses publication but blocking metadata work may continue until its bounded deadline/revocation. Page/manifest IOException and temporary408/429/5xx report unavailable rather than incompatible; malformed/unsafe metadata retains website fallback, icon failures retain placeholder fallback. DEBUG diagnostics log only fixed phase/error class/status/MIME class/count/elapsed, never URL/host/body/PIN/exception messages. Original8second connect/read,12second call and45second operation limits remain.

**VPN DNS authority — resolved in source and exact-source runtime.** Strict packet evidence from predecessora552 showed fixture DNS on host default resolver despite requested VPN Network before platform resolver configuration; production shared the same Network.getAllByName API. This was treated as an Important authority risk, not exempted as fixture noise. No speculative claim is made about an unverified internal AOSP selection branch. All affected production system lookups now use OwnedVpnDns: current UID-owned VPN capabilities and captured LinkProperties/interface/DNS list, numeric configured first server, UDP bound to the captured Network before connect/send. Only a verified truncated reply permits TCP to the same server on the same bound Network. There is no system resolver, alternate DNS/server/route or query retry. Public destination filters remain at callers. Numeric destinations cause no name lookup.

The wire codec bounds hostname/packet/count/RDATA and validates ID/question/types/flags/rcode, backwards bounded compression, relevant answer/CNAME chain only; unrelated additional addresses are ignored and alias cycles rejected. Authority/identical-LinkProperties watcher closes registered resources every50ms;100ms receive polling makes cancellation observable independently of close waiting behind reads. UDP sends once; TCP preserves partial prefix/body offsets. Original absolute3second receive/8second total limits are checked before/after reads; buffers are wiped. All main-source getAllByName occurrences were removed. Existing supported WireGuard policy requires full IPv4 tunnel; optionalIPv6 must also be fully routed, so unsupported IPv6-only availability was not used to expand scope. No NAT64 rewrite or ambient fallback introduced.

**Transfers/documents.** Pending downloads bind originating view/document/revision; Primary background-tab callbacks are refused. Downloads require supported complete GET responses and bounded MIME/size, cancellation and explicit retry; no POST/blob replay or byte-range resume claim. Primary generic downloads and HiddenVPN paths use captured VPN sockets and OwnedVpnDns; HiddenTor keeps authenticated private proxy/remote DNS. Upload selection binds view/origin/generation and cancels on navigation/switch. Already consented website bytes cannot be recalled; session revocation closes broker-owned descriptors/readers. Real retained-descriptor revocation remains covered by nativeTor checks.

PDF/text/CSV are bounded encrypted inert data. EOF validation precedes publication, plaintext validation buffers close/wipe, and partial staging is removed. Document reads check size/hash/authority, including17MiB PDF beyond the old media limit. Text/CSV render literally; PDF uses bounded native pages over in-memory read-only descriptors. No public plaintext preview file was found. Trash/restore/export keep egress authority; explicit document export uses Downloads and compatible Hidden recovery journal tags.

**Screenshots.** Capture retains explicit confirmation, originating vault/document/revision, allowed policy, focus/attachment/bounds and secure-window refusal. FLAG_SECURE is never cleared by capture. Video/embedded/native surfaces and JS-disabled pages are conservatively refused; Tor JS is never enabled for capture. PixelCopy only, no WebView.draw fallback; only NO_DATA retries are bounded and fully fenced. Source bytes are wiped on consumption/rejection. Hidden uses a bounded reliable PNG pipe and parent encrypted staging/PNG validation/revocation/expiry. Correct ownership transfer uses PARCELABLE_WRITE_RETURN_VALUE; ordinary original read-end close had sent premature reliable-close status, independently confirmed against primary AOSP ParcelFileDescriptor source. Actual encrypted Hidden PNG persistence is covered, not merely source composition.

**Privacy/preservation.** Actual Panic Exit/Hide Content actions and originating controller invalidation are selected. Existing177 recovery/docs/upgrade evidence remain unchanged. No destructive migration, replacement key, uninstall/application-data clearing, signer access or signing execution occurred in this review. Physical Samsung Fold/OEM WebView acceptance remains distinct from synthetic widths/themes/fonts/accessibility. DEBUG-only60second SOCKS setup allowance is selected solely by two cold controlled-onion fixture probes; production default handshake/data read30seconds and functional page deadlines remain unchanged.

## Original scope and evidence mapping

The current complete aggregate has passed. Original-APK preservation execution remains a mandatory separate signing-stage gate, explicitly identified below; source presence alone was not treated as runtime proof.

| Required scope | Reviewed verification boundary |
|---|---|
| encrypted_catalogue | Host/JVM bounded format, corruption, restart, stale-authority/commit refusal; actual Primary/Hidden CRUD |
| vault_and_transport_ownership | Actual Activity factory, separate authenticated Hidden processes/modes, stale broker/owner refusal |
| library_and_app_controls | Compose CRUD/launch/settings/switcher plus actual native saved-app launch |
| ordering_and_favourites | Atomic command/JVM checks and shared library/controller coverage |
| pwa_discovery_and_fallback | Parser/transport JVM and actual synthetic public discovery over authenticated VPN/Tor broker |
| bounded_website_and_custom_icons | Decode/geometry/size checks, encrypted restart, chooser/cancel/revoked editor cases |
| fresh_pin_authentication | Wrong/cancel/correct/single-use/thread/revocation tests and actual separate Hidden PIN flow |
| honest_shared_profile_session_controls | Confirmed whole-profile settings, actual cookie/localStorage clear before renderer reentry |
| hidden_ephemeral_login_cleanup | Native private-profile cookies, child retirement/roots cleanup and fresh authenticated lifetime |
| owned_uploads_and_downloads | Actual controlled GET/POST body/hash, explicit selection/confirmation, restricted picker, encrypted persistence |
| encrypted_document_lifecycle | PDF/text/CSV integrity, preview/reopen/trash/restore,17MiB boundary/revocation and real document transfer |
| explicit_screenshot_policy | Pixel assertions, secure/JS/video refusal, mid-capture/result revocation, wipe and actual Hidden pipe persistence |
| cancellation_and_stale_callback_rejection | Actual broker cancel/no staging/fresh retry, upload cancel/navigation/fresh selection, stale launch/icon/PIN/download checks |
| vpn_tor_dns_packet_isolation | Strict packet/DNS/STUN validators; real UDP→TCP bound DNS and pending DNS authority revocation; no default resolver exception |
| revocation_and_process_death | Native continuous UDP/death barrier, network loss/old capability refusal, Tor sink and held descriptor revocation |
| light_theme_fold_and_accessibility | Themes/font/cover-inner layout, actual native Fold size traffic and fresh accessibility hierarchy; physical owner check remains |
| existing_media_pin_collections_settings_browser_preservation | Reviewed original-APK upgrade fixture seeds both vault media/keys/collections, browser metadata/preferences/persistent synthetic Primary cookie, credentials/privacy/Panic/Hide and independent encrypted camera options/audio; execution remains signing-stage |
| recently_deleted_preservation | Exact original46 fixture retains encrypted payload IDs/bytes, deletion timestamps/retention and restart; execution remains signing-stage |
| android17_only | API37/actual AVD target/tool version/health checks and strict manifest/run identity |

## Independently validated final evidence

Run 41074139917/job 124125688565 attempt 1: exact source 04b91ae01c64323aac02975672d6046dfba62e30, tree a38946a5e1d58d5cfbde825d041fb426ac743c60. Reviewer independently fetched GitHub run/jobs/artifacts APIs: workflow path .github/workflows/fast-development.yml, eventpush, branchrelease/1.7.8-private-apps, conclusionsuccess, application/JVM and focusedAndroid stepssuccess; broadfast jobskipped. Artifact 12987700481 namedprivate-apps178-checkpoint is unexpired and bound by API to the same source/run. Reviewer independently SHA256-hashed original rawZIP:90e7b48fec4cbeaec3a84187cd8ade05639c817d514af170fe16c21070c4d69e, exactly matching API digest. Parsed archived source-sha.txt matches candidate.

Independent XML parsing:39 unique exactmanifest Android cases and29 unique JVM cases; zero failures/errors/skips/duplicates. Reviewer independently reran exactcase verifier and strict VPN/Tor validators against extracted events/packet capture: all PASS. Source-bound final-gates.json has original_exit_status 0, decision PASS and all four network gatesPASS (owned_vpn, native_tor, dns_and_packet_observation, revocation). No failed predecessor gate was rewritten. Native revocation result shows child death100ms after lockreturn, retainedWireGuard, zero postbarrierSTUN and ownedrootsremoved; actualnetworkloss result has lostnetwork/childdeath/oldcapabilityrefusal/deadnetworkfailure/credentialretention/cleanup all true. Tor JSoff, forms/multipart/download, IPv4/IPv6/UDP/DNS sink and session/processdeath facts all true and independently matched origin/packet validators.

Actual DNS fixture events show tcp-dns-phase A andAAAA queries each UDP→TCP from10.91.0.2; slow-dns-phase pendingUDP observed before unchanged promptrevocation assertion passes. No default-resolver exception was added. Strict packet validation proves fixtureDNS exclusivelypg1762, originHTTP/TLS andSTUN ownedWireGuard route, no onionexternalDNS/Tor directprobe escape and postdeath traffic cutoff. The narrowly constrained host-local adb mDNS classifier was reviewed with seven positive/negative tests and no blanketinterface/port/name exemption. All relevant nonlocalDNS/unicasttraffic stays subject to originalroute assertions.

Healthy renderer evidence independently checked: commandtools 22.0, actualAVDtargetandroid-37.0, SDK 37,15 unchangedSurfaceFlingerPID 515 samples, empty preflight andfinalcrash buffers. Eight host Java programs independently passed locally and CIhoststep isAPIverifiedsuccess. The reviewer independently rechecked protected177recovery/docs/upgrade paths unchanged against comparisonparent.

Reviewedtests exercise actualoriginobservations/physicalinput, not injectedresults. Synchronization fixes wait originatingNetworkretirement/configuredboundDNS, freshoriginGET/finisheddocument, exactfocusedpublishedaddress beforeoneGo, Mainpublication/dialogclosure and freshUiAutomationcache. KnownHTMLcontrols useonephysicaltouch onfreshstablevisiblebounds; optionaloneSHOW_ON_SCREEN revealsactualtarget withoutclickretry. A real inertdebugonly protectedaccessibilitytool remains enabled viaDONT_SUPPRESS and verifiedconnected/listed afterinstallation, preventing emptyautomationproviders without changing productionUI. Its callbacks inspect noeventcontent and perform noactions/network/storage. SettingsPrimary/Hidden are independentimmutableownerfixtures preserving all originalassertions. ControlledcoldTorreadiness isrealboundedprivateSOCKSHTTP200/exactbody. No protocol/body/hash/privacy assertion was removed; no navigationretry-to-green or fallback was introduced.

SyntheticAPI37width/theme/font/Fold cases do not establish physicalSamsungfolding/OEMWebView behavior or owneracceptance. Primarysharedcookie/Hiddenephemeral policy, boundedpublicmetadata/GET-onlytransfer/conservativescreenshot limits remain explicit. Original-keyupgrade andsignedartifactidentity are mandatory beforedelivery, separately below.

## Reviewed release controls and separate signing gate

| Control | SHA256 |
|---|---|
| scripts/private_apps178_cases.json —39exact cases | b4fb7b96cb568e55442758d212783c33879ffdc3eb6b9bcac680b2046e00568f |
| scripts/verify_final_scope178.py | 6bfbb5898f9a1eeafb1a8067b352dbae3a818e290d2cfda18b6b671159987b21 |
| docs/release178/UpgradeFixtureTest.kt | 71f707a77216e3294e7cbb93d2f544bdb89c61c3d8435009f85a7b1d20ade43e |
| scripts/verify_upgrade178_baseline.py | 7e93c9b75d93e2cb7cd7477d4eebc0d2419016fe3a39b275604fef6cfe8b5445 |
| scripts/verify_synthetic_upgrade178.sh | b96b4e4cdcab16a5e2ee148f0dc5c68534415b54f4eaf36c06e3d3267fd6452c |
| scripts/verify_hidden_vpn_fixture1762.py | 2e9827e1869a8ed52b22dd5161cd34c72404c6dc6a75e8a8bc9e4ad5d0b49676 |
| scripts/tests/test_vpn_host_mdns1762.py | dcb81456585143235c24139d6b14cebe96ff28e443aa601a6cdb738798c8c819 |

Final upgrade fixture was reviewed as control-only instrumentation, captured/hash-checked before frozen application checkout. It supersedes the old fixture hash without changing tested application identity. Actual existing camera APIs seed distinct encrypted nondefault configurations/audio in both vaults before ciphertext snapshot; six real Primary browser preferences and a persistent Secure synthetic cookie are seeded/asserted with Main-thread CookieManager calls, off-Main bounded callback wait/flush and restart check. No real credential/network/camera hardware is required; Hidden cookies remain ephemeral. Baseline camera API unchanged. This is preparation, not executed upgrade evidence.

Acceptance178 remains disabled without approved exact-source marker/report/control hashes and API-bound successful workflow/artifact identity. It must use the exact archived original signed1.7.7 APK: source 25aae6d59f79c455e47acc73a5c99f9c8f15242a, run40899438953/artifact 12908558722, archiveSHA25616873a3b24abcb311d21ca11e93e1af1707be42946a817770e4b9d629e790869, APK SHA256696e80071497501d8da6cd8dd3e2c9728a63b0d4abc9e88c4ab46338fef97a2e, original certificate94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17. No rebuilt baseline substitute. Same signed candidate must pass original-key46→47 in-place upgrade/restart and artifact/certificate/hash verification before delivery. No execution of that final upgrade or verified signed APK is claimed here. Physical owner acceptance remains OWNER_REQUIRED.

## Upgrade control compilation correction

Signing/upgrade run 41077235746, acceptance178 job 124136609755, stopped at upgrade-instrumentation compilation before baseline/candidate adb installation. The release coordinator reports frozen-source evidence, original baseline identity/certificate, signer identity, release lint/build/package, debug-service manifest absence and distribution checks passed. Reported failure artifact 12988277663 has SHA256 3a24bed44d710d372ef162e949dab27e66b778b064cfa1d918659bf2035ce98d; this reviewer has not yet independently inspected that archive.

The reviewer independently inspected the exact one-character control-only fix in docs/release178/UpgradeFixtureTest.kt: an omitted outer result.put closing parenthesis in deletedSnapshot is restored. This preserves all recorded Recently Deleted fields and assertions. The syntax defect was missed in the earlier source review; no executed upgrade claim had been made. Corrected fixture SHA256 is 71f707a77216e3294e7cbb93d2f544bdb89c61c3d8435009f85a7b1d20ade43e, now the reviewed control pin above.

Frozen application 04b91ae01c64323aac02975672d6046dfba62e30 and its successful 39 Android/29 JVM/packet evidence are unchanged, so the application-source approval remains valid for the controlled signing/upgrade stage with this corrected fixture. Compilation and exact original-key baseline-to-candidate upgrade/restart remain mandatory and unverified; no delivery approval follows from this correction. The failed signing/upgrade run remains failed and must not be represented as completed preservation evidence.

## Final machine-readable decision
```json
{"source_sha":"04b91ae01c64323aac02975672d6046dfba62e30","decision":"APPROVED","critical_open":0,"important_open":0}
```

Approval binds only this exact source/tree, successful run/artifact and reviewed control hashes. It permits the already-authorized controlled original-key signing/upgrade stage; signed delivery remains contingent on successful exact archived46→47 in-place upgrade/restart and release identity/manifest/distribution checks. Physical acceptance remains OWNER_REQUIRED.
