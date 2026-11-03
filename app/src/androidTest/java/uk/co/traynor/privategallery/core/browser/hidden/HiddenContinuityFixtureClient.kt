package uk.co.traynor.privategallery.core.browser.hidden

import android.app.ActivityManager
import android.content.*
import android.os.*
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No AUTO_CREATE binding: a retired child cannot be restarted by this client. */
internal class HiddenContinuityFixtureClient(private val context:Context):Closeable {
    private val connected=CountDownLatch(1)
    private var binder:IBinder?=null
    private var bound=false
    private val intent=Intent().setClassName(context,"uk.co.traynor.privategallery.core.browser.hidden.HiddenContinuityFixtureService")
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,service:IBinder){binder=service;connected.countDown()}
        override fun onServiceDisconnected(name:ComponentName){binder=null}
    }
    init {
        check(context.getSystemService(ActivityManager::class.java).runningAppProcesses.any {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_vpn_browser"})
        try {bound=context.bindService(intent,connection,0);check(bound && connected.await(10,TimeUnit.SECONDS))}
        catch(failure:Throwable){if(bound){context.unbindService(connection);bound=false};throw failure}
        finally {context.stopService(intent)}
    }
    private fun transact(code:Int,write:(Parcel)->Unit={},read:(Parcel)->Int={0}):Int {
        val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken("debug.continuity17103");write(request);check(checkNotNull(binder).transact(code,request,reply,0));reply.readException();return read(reply)}finally {request.recycle();reply.recycle()}
    }
    fun publishedAt():Long {
        val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken("debug.continuity17103");check(checkNotNull(binder).transact(7,request,reply,0));reply.readException();return reply.readLong()}
        finally {request.recycle();reply.recycle()}
    }
    fun probe(){transact(6)}
    fun arm(){transact(1)}
    fun release(){transact(2)}
    fun status()=transact(3,read={it.readInt()})
    fun gate(callback:IBinder,pause:Boolean=false,target:Int=HiddenVpnBrowserBroker.RESUME_COMMIT){transact(4,write={it.writeStrongBinder(callback);it.writeInt(if(pause)1 else 0);it.writeInt(target)})}
    override fun close(){if(bound){context.unbindService(connection);bound=false};binder=null}
}
