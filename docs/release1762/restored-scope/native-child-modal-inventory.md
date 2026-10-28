# Native child modal inventory

Production route: HiddenVpnBrowserActivity.Browser, shared by native VPN and Tor modes. Interactive activation remains gated. System VPN consent is Android owned.

| App-owned modal | Production content | Direct component coverage |
|---|---|---|
| Settings | HiddenNativeSettingsContent | Selected light/dark,320cover/shortlandscape,2x, scroll, history toggle and desktop callback |
| Tabs | HiddenNativeTabsContent | Bounded lazy viewport, long titles, selection and close callbacks |
| Bookmarks/history | HiddenNativeLibraryContent |100 maximal-looking long URLs, lazy scroll to final selection |
| HTTP authentication | HiddenNativeAuthContent | Selected light/dark,320cover/shortlandscape,2x, credential entry with keyboard, submit/cancel |
| Form resubmission | HiddenNativeResubmitContent | Warning, explicit resend action |
| Website alert/confirm/prompt/before-unload | HiddenNativeWebsiteContent | Long origin/message, prompt keyboard entry, confirmation; parent owns actual WebView routes |
| Upload picker intro | HiddenNativePickerIntro | Restricted/type counts and explicit review; thumbnail grid remains existing production component |
| Upload consent | HiddenNativeUploadConsentContent | Selected light/dark,320cover/shortlandscape,2x, explicit allow after review |
| GET media download | Existing Activity GalleryAlertDialog | Existing live fixture explicit-save coverage; no new component claim |

The direct tests call the exact content functions now used by production, through GalleryAlertDialog and selected PrivateGalleryTheme. Activity retains all existing liveness, current-view, origin, Binder and session checks. Tabs now use a bounded lazy viewport and no nested dialog scrolling. Settings uses GalleryAlertDialog's bounded scrolling body. No transport, broker, authority or lifecycle machinery changed in this extraction.

Validation: git diff --check passes. Android test compilation/execution remains pending CI; these definitions do not establish keyboard behavior, device acceptance, TalkBack, or real website callback correctness until executed. Parent's real native fixture tests remain mandatory for confirm/prompt, HTTP authentication and form resubmission.

Parent completion additions before grouped verification9 (still unexecuted): the direct class now has14 methods, including empty library and actual501-item loading/selection grid at2x in light/short-landscape and dark/cover windows. Activity uses the same grid component. Tabs expose selected semantics/colors and distinct close labels; closing the selected tab restores its actual address/loading state. Auth fields request non-correcting Next/Password-Done keyboard behavior. Both Main handoff library and native library use the same bounded lazy production body. Live tests additionally exercise real confirm/prompt, tabs, HTTPS authentication and POST resubmission. These are source/test additions, not runtime proof.
