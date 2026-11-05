package uk.co.traynor.privategallery.home2

/** Existing saved apps keep production URL policy and stable UUIDs; catalogue URLs are reviewed separately. */
internal object StoreUrl {
 fun canonical(url:String)=uk.co.traynor.privategallery.core.home2.NativeAppBridge.canonicalUrl(url)
 fun key(url:String)=canonical(url)
}
