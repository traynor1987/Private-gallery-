package uk.co.traynor.privategallery.core.browser.hidden

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.*
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Debug APK only. Start only after the real child is alive, then bind without AUTO_CREATE and stop started state. */
class HiddenContinuityFixtureService:Service(),Application.ActivityLifecycleCallbacks {
    private val main=Handler(Looper.getMainLooper())
    private var activity:Activity?=null
    private var armed=false
    private var worker:Thread?=null
    private var release=CountDownLatch(1)
    private val result=AtomicInteger(0)
    private var gate:IBinder?=null
    private var gateAction=0
    private var gateCode=HiddenVpnBrowserBroker.RESUME_COMMIT
    private var installed=false
    @Volatile private var publishedAt=0L
    private fun onMain(action:()->Unit) {
        val done=CountDownLatch(1);main.post {try {action()}finally {done.countDown()}}
        check(done.await(10,java.util.concurrent.TimeUnit.SECONDS))
    }
    private val endpoint=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            check(getCallingUid()==Process.myUid());data.enforceInterface(DESCRIPTOR)
            when(code) {
                ARM -> {onMain {armed=true};reply!!.writeNoException()}
                RELEASE -> {release.countDown();reply!!.writeNoException()}
                PROBE -> {onMain {(application as HiddenContinuityFixtureApplication).startProbe()};reply!!.writeNoException()}
                TIMING -> {reply!!.writeNoException();reply.writeLong(publishedAt)}
                STATUS -> {reply!!.writeNoException();reply.writeInt(result.get())}
                GATE -> {val callback=data.readStrongBinder();val action=data.readInt();val target=data.readInt();check(target in setOf(HiddenVpnBrowserBroker.SUSPEND,HiddenVpnBrowserBroker.RESUME_COMMIT));onMain {gate=callback;gateAction=action;gateCode=target;installGate()};reply!!.writeNoException()}
                else -> return super.onTransact(code,data,reply,flags)
            }
            return true
        }
    }
    override fun onCreate(){super.onCreate();activity=(application as HiddenContinuityFixtureApplication).current;application.registerActivityLifecycleCallbacks(this)}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {return START_NOT_STICKY}
    override fun onBind(intent:Intent):IBinder=endpoint
    override fun onDestroy(){application.unregisterActivityLifecycleCallbacks(this);release.countDown();worker?.interrupt();gate=null;activity=null;super.onDestroy()}
    override fun onActivityPaused(value:Activity) {
        if(value !is HiddenVpnBrowserActivity)return
        activity=value
        if(armed) {
            armed=false
            val token=value.javaClass.getDeclaredMethod("captureIoToken").apply {isAccessible=true}.invoke(value) as String
            release=CountDownLatch(1);result.set(1)
            worker=Thread({
                try {
                    release.await()
                    val method=value.javaClass.declaredMethods.single {it.name=="call" && it.parameterCount==4}.apply {isAccessible=true}
                    val write:(Parcel)->Unit={it.writeString("738295")}
                    val read:(Parcel)->Boolean={it.readInt()==1}
                    method.invoke(value,HiddenVpnBrowserBroker.VERIFY_PRIVATE_APP_PIN,write,read,token)
                    result.set(3) // Even a false PIN response proves stale work was admitted.
                }catch(e:InvocationTargetException){result.set(if(e.cause is SecurityException || e.cause is IllegalStateException)2 else 4)}
                catch(_:InterruptedException){result.set(4)}
                catch(_:Throwable){result.set(4)}
            },"continuity-old-worker").also {it.start()}
        }
        installGate()
    }
    private fun installGate() {
        val value=activity ?: return
        if(installed || gate==null)return
        val field=value.javaClass.getDeclaredField("broker").apply {isAccessible=true}
        val original=field.get(value) as IBinder
        field.set(value,object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                if(code==gateCode) {
                    // Runs on child Main immediately before the selected production transaction.
                    val callback=gate;gate=null
                    if(callback!=null) {
                        val request=Parcel.obtain();val response=Parcel.obtain()
                        try {request.writeInterfaceToken(DESCRIPTOR);request.writeInt(if(value.getSystemService(android.net.ConnectivityManager::class.java).boundNetworkForProcess==value.javaClass.getDeclaredField("network").apply {isAccessible=true}.get(value))1 else 0);request.writeLong(publishedAt);callback.transact(BOUND,request,response,0);response.readException()}
                        finally {request.recycle();response.recycle()}
                        if(gateAction==1)value.javaClass.getDeclaredMethod("onPause").apply {isAccessible=true}.invoke(value)
                    }
                }
                val accepted=original.transact(code,data,reply,flags)
                if(code==HiddenVpnBrowserBroker.RESUME_TRANSPORT)publishedAt=SystemClock.elapsedRealtime()
                return accepted
            }
        });installed=true
    }
    override fun onActivityCreated(a:Activity,b:Bundle?){}
    override fun onActivityStarted(a:Activity){}
    override fun onActivityResumed(a:Activity){if(a is HiddenVpnBrowserActivity)activity=a}
    override fun onActivityStopped(a:Activity){}
    override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
    override fun onActivityDestroyed(a:Activity){if(activity===a)activity=null}
    companion object {
        const val DESCRIPTOR="debug.continuity17103"
        const val ARM=1;const val RELEASE=2;const val STATUS=3;const val GATE=4;const val BOUND=5;const val PROBE=6;const val TIMING=7
    }
}
