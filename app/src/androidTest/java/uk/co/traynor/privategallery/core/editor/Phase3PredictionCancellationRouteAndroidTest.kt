package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Actual cancellation finally sites, public fake seam; no Native ownership claim. */
@RunWith(AndroidJUnit4::class)
class Phase3PredictionCancellationRouteAndroidTest {
    @Test fun seedreamExplicitCancelUsesDedicatedRoute()=runBlocking{exercise(0)}
    @Test fun modelEditExplicitCancelUsesDedicatedRoute()=runBlocking{exercise(1)}
    @Test fun generationExplicitCancelUsesDedicatedRoute()=runBlocking{exercise(2)}
    @Test fun ordinaryFailureNeverRequestsRemoteCancellation()=runBlocking{
        for(kind in 0..2){val t=CancelTransport(ordinaryFailure=true)
            try{invoke(kind,t);fail("ordinary error missing")}catch(_:AiEditFailure){}
            assertEquals(1,t.posts);assertEquals(0,t.cancels);assertEquals(0,t.rawCancels);assertEquals(0,t.downloads)
        }
    }
    @Test fun failedBestEffortCancellationIsNotRetriedOrResubmitted()=runBlocking{
        for(kind in 0..2)exercise(kind,failed=true)
    }
    private suspend fun exercise(kind:Int,failed:Boolean=false)=coroutineScope{
        val t=CancelTransport(failed=failed)
        val job=launch{invoke(kind,t)}
        t.entered.await();job.cancel();job.join()
        assertEquals(1,t.posts);assertEquals(1,t.cancels);assertEquals(0,t.rawCancels);assertEquals(0,t.downloads)
        if(!failed)assertTrue(t.cancelBytes!!.all{it==0.toByte()})
    }
    private suspend fun invoke(kind:Int,t:AiHttpTransport):ByteArray=when(kind){
        0->ReplicateSeedreamApi(t,1).edit("synthetic-token".toByteArray(),byteArrayOf(1),"public edit")
        1->ReplicateModelEditApi(t,1).edit("synthetic-token".toByteArray(),ReplicateEditModel.SEEDREAM,byteArrayOf(1),"public edit",null)
        else->ReplicateImageGenerationApi(t,1).generate("synthetic-token".toByteArray(),GenerationRequest(GenerationModel.SEEDREAM,"public create",GenerationAspect.SQUARE))
    }
    private class CancelTransport(private val ordinaryFailure:Boolean=false,private val failed:Boolean=false):AiHttpTransport{
        val entered=CompletableDeferred<Unit>();var posts=0;var cancels=0;var rawCancels=0;var downloads=0;var cancelBytes:ByteArray?=null
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse=when{
            request.url.endsWith("/cancel")-> {rawCancels++;json("{}")}
            request.method=="POST"->{posts++;json("{\"id\":\"PublicId42\",\"status\":\"processing\"}")}
            else->{downloads++;throw AssertionError("unexpected legacy GET")}
        }
        override suspend fun consumePredictionStatus(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){
            if(ordinaryFailure)throw AiEditFailure("public ordinary failure")
            entered.complete(Unit);awaitCancellation()
        }
        override suspend fun consumePredictionCancellation(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){
            cancels++;assertEquals("PublicId42",expectedId);assertEquals("https://api.replicate.com/v1/predictions/PublicId42/cancel",request.url)
            assertEquals("POST",request.method);assertNull(request.body);assertEquals(2*1024*1024,request.maxResponseBytes)
            if(failed)throw AiNetworkFailure(true)
            val response=json("{}");cancelBytes=response.bytes
            try{consume(response)}finally{response.bytes.fill(0)}
        }
        private fun json(s:String)=AiHttpResponse(200,"application/json",s.toByteArray())
    }
}
