package uk.co.traynor.privategallery.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*

/** Actual production UI and ActivityResultRegistry callbacks with controlled external results.
 * No camera/location permission is granted or website contacted. Device positives use
 * separately granted, synthetic test-provider documents only. */
class BrowserPermissionOwnership1762Test {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private class Registry:ActivityResultRegistry(),ActivityResultRegistryOwner {
        override val activityResultRegistry get()=this
        val launches=mutableListOf<Pair<Int,Any?>>()
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){
            launches+=requestCode to if(input is Array<*>)input.toList() else input
        }
        fun <T> result(index:Int,value:T){assertTrue(dispatchResult(launches[index].first,value))}
        fun <T> resultCode(code:Int,value:T){assertTrue(dispatchResult(code,value))}
    }
    private class Permission(var site:String,var requested:Array<String>):PermissionRequest() {
        val grants=mutableListOf<List<String>>();var denied=0
        override fun getOrigin()=Uri.parse(site)
        override fun getResources()=requested
        override fun grant(resources:Array<out String>){grants+=resources.toList()}
        override fun deny(){denied++}
    }
    private class Params(private val multiple:Boolean=false,private val accept:Array<String> = arrayOf("image/png"),private val access:Int=PERMISSION_MODE_READ,private val explicitMode:Int?=null):WebChromeClient.FileChooserParams() {
        override fun getMode()=explicitMode ?: if(multiple)MODE_OPEN_MULTIPLE else MODE_OPEN
        override fun getAcceptTypes()=accept
        override fun getPermissionMode()=access
        override fun isCaptureEnabled()=false
        override fun getTitle():CharSequence?=null
        override fun getFilenameHint():String?=null
        override fun createIntent()=android.content.Intent()
    }
    private lateinit var session:BrowserV2Session
    private val mounted=mutableStateOf(true)
    private val registryOwner=mutableStateOf<Registry>(Registry())
    private var previousPolicy:BrowserUploadPolicy?=null
    private val fixture=Uri.parse("content://uk.co.traynor.privategallery.test.browser-selection-fixture")
    private val grantedFiles=mutableListOf<Uri>()
    private fun selectedFile(slow:Boolean=false):Uri {
        val uri=checkNotNull(compose.activity.contentResolver.call(fixture,"grant",if(slow)"slow" else null,null)).getParcelable("uri",Uri::class.java)!!
        grantedFiles+=uri;return uri
    }
    private fun mount() {
        val base=compose.activity
        previousPolicy=BrowserUploadPreference.read(base);BrowserUploadPreference.write(base,BrowserUploadPolicy.VAULT_AND_DEVICE)
        val denied=object:ContextWrapper(base) {
            override fun checkSelfPermission(permission:String)=PackageManager.PERMISSION_DENIED
            override fun checkPermission(permission:String,pid:Int,uid:Int)=PackageManager.PERMISSION_DENIED
        }
        session=BrowserV2Session(base,BrowserVpnGate{true},NoopBrowserV2Listener)
        compose.setContent {CompositionLocalProvider(LocalContext provides denied,LocalActivityResultRegistryOwner provides registryOwner.value) {
            PrivateGalleryTheme {if(mounted.value)BrowserV2Home(session,BrowserSearchEngine.GOOGLE,{_,_->},{_,_->},false,{},emptyList(),{_,_,_->},{},{it(emptyList())},{it()},{},staticContentHost=true)}
        }}
        compose.waitForIdle()
        compose.runOnIdle{assertNotNull(session.activeWebViewOrNull())}
    }
    private fun capability(request:Permission) {compose.runOnIdle{session.onPermissionRequest(session.tabs.activeTab.id,request)}}
    private fun geo(origin:String,callback:(String,Boolean)->Unit) {compose.runOnIdle{session.onGeolocationRequest(session.tabs.activeTab.id,origin){site,allow,_->callback(site,allow)}}}
    private fun file(multiple:Boolean=false,received:MutableList<List<Uri>?>) {compose.runOnIdle{assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,ValueCallback{received+=it?.toList()},Params(multiple)))}}
    private fun chooseDevice(){compose.onNodeWithText("Choose from device").performClick()}
    @After fun cleanup(){
        if(::session.isInitialized){compose.runOnIdle{mounted.value=false};compose.waitForIdle();compose.runOnIdle{session.destroyAll()}}
        previousPolicy?.let{BrowserUploadPreference.write(compose.activity,it)}
        grantedFiles.forEach {uri ->runCatching {compose.activity.contentResolver.call(fixture,"revoke",null,Bundle().apply{putParcelable("uri",uri)})}}
    }
    @Test fun cameraResultCannotGrantReplacementMicrophoneOrClearItsConsent() {
        mount();val a=Permission("https://camera-a.example",arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE));val b=Permission("https://microphone-b.example",arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        capability(a);compose.onNodeWithText("https://camera-a.example").assertIsDisplayed();compose.onNodeWithText("Allow this time").performClick()
        compose.runOnIdle{assertEquals(listOf(Manifest.permission.CAMERA),registryOwner.value.launches.single().second)}
        capability(b)
        compose.runOnIdle{registryOwner.value.result(0,mapOf(Manifest.permission.CAMERA to true))}
        compose.onNodeWithText("https://microphone-b.example").assertIsDisplayed()
        compose.runOnIdle{assertEquals(1,a.denied);assertTrue(a.grants.isEmpty());assertEquals(0,b.denied);assertTrue(b.grants.isEmpty())}
        compose.onNodeWithText("Allow this time").performClick()
        compose.runOnIdle{assertEquals(listOf(Manifest.permission.RECORD_AUDIO),registryOwner.value.launches[1].second);registryOwner.value.result(1,mapOf(Manifest.permission.RECORD_AUDIO to true))}
        compose.runOnIdle{assertEquals(listOf(listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)),b.grants);assertEquals(1,a.denied)}
    }
    @Test fun locationResultKeepsExactOriginAndReplacementNeedsItsOwnConsent() {
        mount();val a=mutableListOf<Pair<String,Boolean>>();val b=mutableListOf<Pair<String,Boolean>>()
        geo("https://location-a.example/"){site,allow->a+=site to allow};compose.onNodeWithText("Allow this time").performClick()
        geo("https://location-b.example/"){site,allow->b+=site to allow}
        compose.runOnIdle{registryOwner.value.result(0,mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))}
        compose.onNodeWithText("https://location-b.example").assertIsDisplayed()
        compose.runOnIdle{assertEquals(listOf("https://location-a.example/" to false),a);assertTrue(b.isEmpty())}
        compose.onNodeWithText("Allow this time").performClick()
        compose.runOnIdle{registryOwner.value.result(1,mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to true))}
        compose.runOnIdle{assertEquals(listOf("https://location-b.example/" to true),b);assertEquals(1,a.size)}
    }
    @Test fun cancelledDeviceAUriCannotReachReplacementBOrEraseItsChoice() {
        mount();val a=mutableListOf<List<Uri>?>();val b=mutableListOf<List<Uri>?>()
        file(received=a);chooseDevice();file(received=b)
        compose.runOnIdle{registryOwner.value.result(0,listOf(Uri.parse("content://synthetic-selected-by-a/photo.png")))}
        compose.onNodeWithText("Choose file for website").assertIsDisplayed()
        compose.runOnIdle{assertEquals(listOf<List<Uri>?>(null),a);assertTrue(b.isEmpty())}
        chooseDevice();val selected=selectedFile()
        compose.runOnIdle{registryOwner.value.result(1,listOf(selected))}
        compose.waitUntil(10_000){b.size==1}
        compose.runOnIdle{assertEquals(listOf(listOf(selected)),b);assertEquals(1,a.size)}
    }
    @Test fun cancelledSingleMarkerBlocksMultipleReplacementUntilActualResultAndCapsFour() {
        mount();val a=mutableListOf<List<Uri>?>();val b=mutableListOf<List<Uri>?>()
        file(received=a);chooseDevice();file(multiple=true,received=b);chooseDevice()
        compose.runOnIdle{assertEquals(1,registryOwner.value.launches.size);assertTrue(b.isEmpty())}
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle{registryOwner.value.result(0,listOf(Uri.parse("content://synthetic-a/never-forward")))}
        chooseDevice();val selected=(0..5).map{selectedFile()}
        compose.runOnIdle{registryOwner.value.result(1,selected)}
        compose.waitUntil(10_000){b.size==1}
        compose.runOnIdle{assertEquals(listOf(selected.take(4)),b);assertEquals(listOf<List<Uri>?>(null),a)}
    }
    @Test fun documentNavigationAndBackgroundRevokeCallbacksWhileAndroidMarkerStaysReserved() {
        mount();val a=Permission("https://a.example",arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        capability(a);compose.onNodeWithText("Allow this time").performClick()
        compose.runOnIdle{val tab=session.tabs.activeTab;session.onPageState(tab.id,tab.url,"Reloaded",true,false,false)}
        compose.runOnIdle{registryOwner.value.result(0,mapOf(Manifest.permission.CAMERA to true));assertEquals(1,a.denied);assertTrue(a.grants.isEmpty())}
        val b=mutableListOf<List<Uri>?>();file(received=b);chooseDevice()
        compose.runOnIdle{mounted.value=false};compose.waitForIdle()
        compose.runOnIdle{registryOwner.value.result(1,listOf(Uri.parse("content://synthetic-after-background/no-file")));assertEquals(listOf<List<Uri>?>(null),b)}
    }
    @Test fun stableRetainedKeysConsumeCancelledResultsAcrossUiAndRegistryRecreation() {
        mount();val a=Permission("https://before-recreation.example",arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        capability(a);compose.onNodeWithText("Allow this time").performClick()
        lateinit var retained:BrowserPermissionOwners;var code=0;val saved=Bundle()
        compose.runOnIdle{retained=ViewModelProvider(compose.activity)[BrowserPermissionOwners::class.java];code=registryOwner.value.launches.single().first;registryOwner.value.onSaveInstanceState(saved);mounted.value=false}
        compose.waitForIdle()
        compose.runOnIdle{val replacement=Registry();replacement.onRestoreInstanceState(saved);registryOwner.value=replacement;mounted.value=true}
        compose.waitForIdle()
        val b=Permission("https://after-recreation.example",arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE));capability(b)
        compose.runOnIdle{assertSame(retained,ViewModelProvider(compose.activity)[BrowserPermissionOwners::class.java]);registryOwner.value.resultCode(code,mapOf(Manifest.permission.CAMERA to true))}
        compose.onNodeWithText("https://after-recreation.example").assertIsDisplayed()
        compose.runOnIdle{assertEquals(1,a.denied);assertTrue(a.grants.isEmpty());assertTrue(b.grants.isEmpty());assertEquals(0,b.denied)}
        compose.onNodeWithText("Allow this time").performClick()
        compose.runOnIdle{registryOwner.value.result(0,mapOf(Manifest.permission.CAMERA to true));assertEquals(1,b.grants.size)}
    }
    @Test fun inactiveTabRequestsAreConsumedWithoutPermissionOrFileFallbackUi() {
        mount();val camera=Permission("https://inactive.example",arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE));val geo=mutableListOf<Boolean>();val files=mutableListOf<List<Uri>?>()
        compose.runOnIdle{
            val inactive=session.tabs.activeTab.id;session.newTab();assertNotNull(session.activeWebViewOrNull())
            session.onPermissionRequest(inactive,camera);session.onGeolocationRequest(inactive,"https://inactive.example"){_,allow,_->geo+=allow}
            assertTrue(session.onShowFileChooser(inactive,ValueCallback{files+=it?.toList()},Params()))
            assertEquals(1,camera.denied);assertTrue(camera.grants.isEmpty());assertEquals(listOf(false),geo);assertEquals(listOf<List<Uri>?>(null),files);assertTrue(registryOwner.value.launches.isEmpty())
        }
        compose.onAllNodesWithText("Allow this time").assertCountEquals(0)
        compose.onAllNodesWithText("Choose file for website").assertCountEquals(0)
    }
    @Test fun actualActivityStopCancelsLocationOnceAndLateAndroidGrantCannotReviveIt() {
        mount();val decisions=mutableListOf<Boolean>()
        geo("https://stopped.example"){_,allow->decisions+=allow};compose.onNodeWithText("Allow this time").performClick()
        val registry=registryOwner.value
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {registry.result(0,mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))}
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.runOnIdle {assertEquals(listOf(false),decisions)}
        compose.onAllNodesWithText("Allow this time").assertCountEquals(0)
    }
    @Test fun ownedDevicePickerSurvivesActualStopAndDefersEarlyResultUntilResume() {
        mount();val received=mutableListOf<List<Uri>?>();val selected=selectedFile()
        file(received=received);chooseDevice()
        val registry=registryOwner.value
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue("Owned picker stop must preserve its website callback",received.isEmpty())
            registry.result(0,listOf(selected))
            assertTrue("Activity result before ON_START must not deliver in background",received.isEmpty())
        }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue("STARTED is not RESUMED",received.isEmpty())
        }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000){received.size==1}
        compose.runOnIdle{assertEquals(listOf(listOf(selected)),received)}
    }

    @Test fun deviceSelectionCapturesAcceptModeAndRechecksTightenedPolicyBeforeDelivery() {
        mount();val selected=mutableListOf<List<Uri>?>();val accepts=arrayOf("image/png")
        compose.runOnIdle {
            assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,ValueCallback{selected+=it?.toList()},Params(false,accepts)))
            accepts[0]="video/mp4"
        }
        chooseDevice()
        compose.runOnIdle {
            assertEquals(listOf("image/png"),registryOwner.value.launches.single().second)
            BrowserUploadPreference.write(compose.activity,BrowserUploadPolicy.VAULT_ONLY)
            registryOwner.value.result(0,listOf(Uri.parse("content://synthetic-picker/no-longer-authorized.png")))
            assertEquals(listOf<List<Uri>?>(null),selected)
        }
    }

    @Test fun retainedCancelledReservationSurvivesActualActivityRecreationWithoutGrantingNewOwner() {
        lateinit var before:BrowserPermissionOwners
        val grants=java.util.concurrent.atomic.AtomicInteger();val denied=java.util.concurrent.atomic.AtomicInteger()
        compose.activityRule.scenario.onActivity {activity ->
            before=ViewModelProvider(activity)[BrowserPermissionOwners::class.java]
            val a=BrowserExternalResultGate.Owner<Map<String,Boolean>>({grants.incrementAndGet()},{denied.incrementAndGet()})
            assertTrue(before.capability.reserve(a));a.cancel()
        }
        val key=before.capabilityKey
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity {activity ->
            val after=ViewModelProvider(activity)[BrowserPermissionOwners::class.java]
            assertSame(before,after);assertEquals(key,after.capabilityKey);assertTrue(after.capability.busy())
            val b=BrowserExternalResultGate.Owner<Map<String,Boolean>>({grants.incrementAndGet()},{denied.incrementAndGet()})
            assertFalse(after.capability.reserve(b));after.capability.result(mapOf(Manifest.permission.CAMERA to true))
            assertEquals(0,grants.get());assertEquals(1,denied.get())
            assertTrue(after.capability.reserve(b));after.capability.result(mapOf(Manifest.permission.CAMERA to true));assertEquals(1,grants.get())
        }
    }

    @Test fun privateDeviceResultIsRejectedWithExplanationWithoutReadingPrivateFile() {
        mount();val received=mutableListOf<List<Uri>?>()
        val privateFile=java.io.File(compose.activity.filesDir,"device-uri-owner-${java.util.UUID.randomUUID()}").apply{writeText("Synthetic private marker")}
        try {
            file(received=received);chooseDevice()
            compose.runOnIdle{registryOwner.value.result(0,listOf(Uri.fromFile(privateFile)))}
            compose.waitUntil(10_000){received.size==1}
            compose.runOnIdle{assertEquals(listOf<List<Uri>?>(null),received);assertEquals("Synthetic private marker",privateFile.readText())}
            compose.onNodeWithText("Only readable Android content documents can be selected from the device.").assertIsDisplayed()
        }finally{privateFile.delete()}
    }
    @Test fun writeCapableAndFolderRequestsAreConsumedWithoutLaunchingAnyPicker() {
        mount();val result=mutableListOf<List<Uri>?>()
        compose.runOnIdle {
            assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,ValueCallback{result+=it?.toList()},Params(access=WebChromeClient.FileChooserParams.PERMISSION_MODE_READ_WRITE)))
            for(mode in listOf(WebChromeClient.FileChooserParams.MODE_OPEN_FOLDER,WebChromeClient.FileChooserParams.MODE_SAVE))
                assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,ValueCallback{result+=it?.toList()},Params(explicitMode=mode)))
            assertEquals(listOf<List<Uri>?>(null,null,null),result);assertTrue(registryOwner.value.launches.isEmpty())
        }
        compose.onNodeWithText("Only read-only file uploads are supported. Website file editing, saving and folder access are blocked.").assertIsDisplayed()
    }

    @Test fun cancelledDeviceValidationCannotDeliverLateReadableUri() {
        mount();val received=mutableListOf<List<Uri>?>();val selected=selectedFile(slow=true)
        file(received=received);chooseDevice()
        compose.runOnIdle{registryOwner.value.result(0,listOf(selected))}
        compose.onNodeWithText("Checking selected files").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle{assertEquals(listOf<List<Uri>?>(null),received)}
        Thread.sleep(1_200) // The external provider finishes after the cancelled owner.
        compose.runOnIdle{assertEquals(listOf<List<Uri>?>(null),received)}
    }

}
