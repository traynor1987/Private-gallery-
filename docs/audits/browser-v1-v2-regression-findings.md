# Browser V1 → V2 regression findings

Status: pre-device-acceptance source analysis. This document intentionally does not claim that a physical compatibility defect is solved until the controlled signed acceptance build is tested.

## Evidence and classifications

| Area | V1 | V2 | Classification |
|---|---|---|---|
| WebView ownership | Browser composable and activity state both participated in retaining/rebinding the view. Device traces recorded rebinding while an affected page was visually incomplete. | `BrowserV2Session` owns one WebView per tab; Compose only attaches the selected instance by stable tab id. | STRONGLY SUPPORTED contributor |
| Compose lifecycle | Browser callbacks, acquisition feedback and route state could pass through the same UI path that hosted the WebView. | UI state does not construct, configure, or destroy a tab WebView; tabs are explicitly closed, evicted at the resource limit, or recovered after renderer loss. | STRONGLY SUPPORTED contributor |
| Focus | V1 device traces showed a visible Browser WebView with native and document focus false. | V2 WebViews are touch-focusable and navigation requests focus only after attachment; page taps retain normal WebView focus behaviour. | POSSIBLE CONTRIBUTOR — controlled physical focus observation required |
| Resource interception | V1 accumulated compatibility/acquisition hooks while device evidence showed ordinary subresources. | V2 has no `shouldInterceptRequest` path and acquisition begins only from download/long-press/screenshot user actions. | STRONGLY SUPPORTED contributor |
| Popup handling | V1 investigation did not prove a child window callback for the blank modal. | V2 handles `onCreateWindow` with a tab-owned WebView and `WebViewTransport`; ordinary in-page modals remain in the parent WebView. | UNRELATED to an in-page modal unless device trace later records a child-window request |
| Cookies/storage | V1 settings changed during compatibility work. | V2 deliberately enables JavaScript, DOM storage, first-party cookies, IndexedDB/WebView profile support and service workers; third-party cookies remain privacy-conscious disabled. | POSSIBLE CONTRIBUTOR |
| VPN gate | V1 had history of navigation/lifecycle gating changes. | V2 checks only remote navigations and user-initiated remote acquisition; it never intercepts ordinary page subresources after the gate opens. | STRONGLY SUPPORTED contributor |
| Renderer failure | Android reported a WebView-provider crash in physical testing. | V2 handles `onRenderProcessGone`, destroys only the invalid tab WebView, retains safe tab metadata, and offers reload through the normal gate. | CONFIRMED recovery gap addressed; provider crash root cause remains UNKNOWN |

## Controlled physical acceptance needed

1. Test complex modern HTTPS application with the V2 acceptance console. Confirm page UI and interaction, then record focus state without storing site identity.
2. Test modal-heavy HTTPS application. Determine whether V2 records a child-window event; if not, classify the historical V1 popup theory as unrelated.
3. Test a fold/window-size transition and tab switching. No `WEBVIEW_CREATED` event is expected for an already-open tab.
4. Test renderer recovery where the provider permits safe reproduction.

No website names, URLs, page content, cookies, credentials or console payloads belong in this document or the production diagnostic stream.
