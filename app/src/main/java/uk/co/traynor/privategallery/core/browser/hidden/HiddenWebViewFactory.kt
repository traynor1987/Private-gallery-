package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Context
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import uk.co.traynor.privategallery.core.browser.v2.applyV1SafetyDefaults

/** The same restricted renderer configuration is used in production and instrumentation. */
internal object HiddenWebViewFactory {
    fun create(context: Context, profileName: String): WebView {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        require(profileName.startsWith("hidden16-"))
        val view=WebView(context)
        WebViewCompat.setProfile(view,profileName)
        view.settings.apply {
            blockNetworkLoads=true;javaScriptEnabled=false;domStorageEnabled=false
            applyV1SafetyDefaults()
            allowContentAccess=false;allowFileAccess=false
            cacheMode=WebSettings.LOAD_NO_CACHE;saveFormData=false;setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(true)
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;safeBrowsingEnabled=false
        }
        view.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest)=HiddenHttpTransport.blocked()
        }
        return view
    }
}
