package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Process
import okhttp3.ConnectionPool
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.privateapps.PrivateAppMetadataTransport

/** Test observations use the real owned DNS/socket path and existing trusted HTTPS fixture. */
internal object OwnedVpnFixtureClient178 {
    private fun request(context:Context,network:Network,path:String,body:String?=null):Pair<Int,String> {
        check(BuildConfig.DEBUG)
        require(path in setOf("/evidence","/probe-revocation"))
        val manager=context.getSystemService(ConnectivityManager::class.java)
        val current={manager.getNetworkCapabilities(network)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.ownerUid==Process.myUid()
        }==true}
        val client=PrivateAppMetadataTransport.vpn(context,network,current)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).cache(null)
            .connectTimeout(3,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS).callTimeout(6,TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS)).build()
        try {
            val request=Request.Builder().url("https://native-phase.fixture.invalid$path").apply {
                if(body!=null)post(body.toRequestBody())
            }.build()
            return client.newCall(request).execute().use {response ->
                check(current());val input=checkNotNull(response.body)
                require(input.contentLength()<=1_048_576)
                val bytes=input.byteStream().readNBytes(1_048_577)
                try {require(bytes.size<=1_048_576);check(current());response.code to bytes.toString(Charsets.UTF_8)}finally{bytes.fill(0)}
            }
        } finally {client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    fun evidence(context:Context,network:Network):JSONObject {
        val result=request(context,network,"/evidence");check(result.first==200);return JSONObject(result.second)
    }
    fun revocationBarrier(context:Context,network:Network):Int =
        request(context,network,"/probe-revocation","child-revoked-udp-barrier").first
}
