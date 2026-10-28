package uk.co.traynor.privategallery.core.browser.hidden

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*
import uk.co.traynor.privategallery.core.vpn.*

class HiddenUploadPickerPaging1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private fun photo(color:Int)=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).let {bitmap ->
        bitmap.eraseColor(color)
        try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {bitmap.recycle()}
    }
    private fun waitFor(message:String,seconds:Int=30,predicate:()->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L
        while(android.os.SystemClock.elapsedRealtime()<deadline) {if(predicate())return;Thread.sleep(100)}
        assertTrue(message,predicate())
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?) {if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(automation.rootInActiveWindow);return result
    }
    private fun click(text:String) {
        waitFor("Actionable $text") {
            val node=nodes().firstOrNull {it.text?.toString()==text || it.contentDescription?.toString()==text} ?: return@waitFor false
            var target:AccessibilityNodeInfo?=node
            while(target!=null && !target.isClickable)target=target.parent
            target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true
        }
    }
    @Test fun worstCaseEscapedMetadataPageRemainsBoundedAndRoundTrips() {
        val source=(0 until 64).map {index ->HiddenUploadPickerQuery.Entry(
            index.toString()+"\u0001".repeat(60),"\u0001".repeat(200),"\u0001".repeat(128),false,true)}
        val snapshot=HiddenUploadPickerQuery(source) {true}
        val response=HiddenVpnBrowserBroker.pickerPage(snapshot,3,"bounded-query",0)
        val parsed=JSONObject(response)
        assertEquals(64,parsed.getJSONArray("items").length())
        assertEquals(source.last().name,parsed.getJSONArray("items").getJSONObject(63).getString("name"))
        assertTrue(response.length<=200_000)
        val parcel=Parcel.obtain()
        try {
            parcel.writeNoException();parcel.writeString(response)
            assertTrue("Even fully escaped metadata has substantial Binder headroom",parcel.dataSize()<400_000)
            parcel.setDataPosition(0);parcel.readException();assertEquals(response,parcel.readString())
        } finally {parcel.recycle()}
    }
    @Test fun actualTransferredLastItemBeyondFiveHundredSurvivesCancelReopenAndTypedPages() {
        val args=InstrumentationRegistry.getArguments()
        val config=args.getString("hiddenVpnConfig")
        assumeTrue("Requires owned VPN fixture for actual authenticated child Binder calls",config!=null)
        check(BuildConfig.DEBUG);require(config=="/data/local/tmp/hidden-vpn1762.conf")
        val base=instrumentation.targetContext
        val root=File(base.noBackupFilesDir,"picker-paging-${UUID.randomUUID()}")
        val suffix=UUID.randomUUID().toString();val preferences=mutableSetOf<String>()
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
            override fun getPackageName()=base.packageName // The real owned VPN must retain the production UID/package.
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {val key="picker-paging-$suffix-$name";preferences+=key;return base.getSharedPreferences(key,mode)}
        }
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){45}))
        val allowed=AtomicBoolean(true)
        val engine=WireGuardVpnEngine(OfficialWireGuardBackend(base))
        var broker:HiddenVpnBrowserBroker?=null
        var primaryKey:ByteArray?=null
        fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes().toString(Charsets.UTF_8)}
        try {
            primaryKey=PinVaultKeyStore(context).create("123456".toCharArray())
            val primary=AndroidVaultRepository(context,checkNotNull(primaryKey))
            val bytes=photo(android.graphics.Color.GREEN)
            val original=try {(primary.importVerified(VaultImportSource("Transferred final-page photo.png","image/png",{bytes.inputStream()})) as ImportResult.Imported).item} finally {bytes.fill(0)}
            PrimaryCopyAccess(context,session).use {access ->access.authenticate("123456".toCharArray());assertEquals(1,access.copy(listOf(original.id)){_,_->}.verified)}
            val hidden=HiddenRepository(context,session)
            val transferred=hidden.items().single()
            val other=photo(android.graphics.Color.BLUE)
            try {
                repeat(501) {hidden.importMedia("Newer photo $it.png","image/png",other.inputStream(),createDistinctCopy=true)}
                hidden.importMedia("Restricted photo.png","image/png",other.inputStream(),createDistinctCopy=true,vaultOnly=true)
                // A valid video exercises MIME exclusion without asking the media importer to
                // manufacture an MP4 thumbnail from PNG bytes (which Android correctly rejects).
                instrumentation.context.assets.open("browser-media/valid.mp4").use {video ->
                    hidden.importMedia("Not an image.mp4","video/mp4",video,createDistinctCopy=true)
                }
            } finally {other.fill(0)}
            val snapshot=HiddenVpnBrowserBroker.pickerSnapshot(hidden.items(),arrayOf(".png"))
            assertEquals(502,snapshot.size());assertEquals(1,snapshot.restrictedCount);assertEquals(1,snapshot.typeCount)
            val ids=mutableListOf<String>();var offset=0
            while(offset<snapshot.size()) {
                val page=HiddenVpnBrowserBroker.pickerPage(snapshot,7,"fixture-query",offset)
                val parcel=Parcel.obtain()
                try {parcel.writeNoException();parcel.writeString(page);assertTrue("Metadata pages must retain substantial Binder margin",parcel.dataSize()<400_000)} finally {parcel.recycle()}
                val json=JSONObject(page);val items=json.getJSONArray("items")
                assertTrue(items.length()<=64);repeat(items.length()){ids+=items.getJSONObject(it).getString("id")};offset=json.getInt("next")
            }
            assertEquals(502,ids.distinct().size);assertTrue("Real transferred item must be beyond removed 500-item cap",ids.indexOf(transferred.id)>=500)
            // Captured metadata does not change under an already open paged query.
            val added=photo(android.graphics.Color.RED)
            try {hidden.importMedia("Added after query.png","image/png",added.inputStream(),createDistinctCopy=true)} finally {added.fill(0)}
            assertEquals(502,snapshot.size())
            val info=automation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.serviceInfo=info
            shell("appops set ${base.packageName} ACTIVATE_VPN allow");assertNull(VpnService.prepare(base))
            val profile=(VpnProfileParser.import("Disposable paging VPN",shell("cat $config")) as VpnProfileImportResult.Accepted).profile
            engine.connect(profile)
            waitFor("Owned VPN connected",90){engine.ownsTunnel && engine.state==VpnConnectionState.CONNECTED}
            val manager=base.getSystemService(ConnectivityManager::class.java);var pinned:Network?=null
            waitFor("Owned VPN network") {pinned=manager.allNetworks.singleOrNull {manager.getNetworkCapabilities(it)?.let {caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.ownerUid==Process.myUid()}==true};pinned!=null}
            val network=checkNotNull(pinned)
            ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                scenario.onActivity {activity ->
                    val isolated=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=context.noBackupFilesDir}
                    broker=HiddenVpnBrowserBroker(isolated,session,network,{allowed.get()&&engine.ownsTunnel&&engine.state==VpnConnectionState.CONNECTED},{})
                    broker!!.launch()
                }
                waitFor("Address input") {nodes().firstOrNull {it.isEditable}?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"http://fixture.invalid/upload")})==true}
                click("Go");click("Choose Hidden upload")
                // First callback is cancelled, then a fresh request must own every page/thumbnail.
                click("Cancel");click("Choose Hidden upload")
                waitFor("Restricted and MIME exclusions are explicitly explained") {nodes().any {it.text?.toString()?.contains("1 Vault-only restricted item(s)")==true && it.text?.toString()?.contains("1 other item(s)")==true}}
                waitFor("All pages loaded before scrolling") {nodes().any {it.contentDescription?.toString()?.startsWith("Newer photo ")==true || it.text?.toString()?.startsWith("Newer photo ")==true}}
                assertFalse(nodes().any {it.text?.toString()=="Restricted photo.png" || it.text?.toString()=="Not an image.mp4"})
                var scrolls=0
                while(nodes().none {it.contentDescription?.toString()==transferred.displayName || it.text?.toString()==transferred.displayName} && scrolls++<180) {
                    val grid=nodes().firstOrNull {it.isScrollable}
                    grid?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(100)
                }
                waitFor("Transferred item beyond 500 reaches actual child") {nodes().any {it.contentDescription?.toString()==transferred.displayName || it.text?.toString()==transferred.displayName}}
                click(transferred.displayName)
                waitFor("No egress before separate confirmation") {nodes().any {it.text?.toString()=="Send media outside Hidden Vault?"}}
                click("Cancel")
                assertTrue(primary.items().any {it.id==original.id})
            }
        } finally {
            allowed.set(false);broker?.close();session.lock();engine.disconnect();primaryKey?.fill(0)
            waitFor("Owned fixture teardown",15){!engine.ownsTunnel}
            root.deleteRecursively();preferences.forEach {base.deleteSharedPreferences(it)}
        }
    }
}
