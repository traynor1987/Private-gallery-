package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual three production poll loops, public fake transport; no Native legacy baseline. */
class Phase3PredictionStatusRouteTest {
    @Test fun seedreamStatusUsesDedicatedLocalConsumption()=runBlocking { exercise(0) }
    @Test fun modelEditStatusUsesDedicatedLocalConsumption()=runBlocking { exercise(1) }
    @Test fun generationStatusUsesDedicatedLocalConsumption()=runBlocking { exercise(2) }
    @Test fun statusFailureRetriesSameGetWithoutNewSubmissionOrCancel()=runBlocking {
        for(kind in 0..2)exercise(kind,transient=true)
    }
    @Test fun changedPredictionIdRejectsBeforeDownloadForEveryLoop()=runBlocking {
        for(kind in 0..2){val transport=StatusTransport(changed=true)
            try{invoke(kind,transport);fail("changed prediction admitted")}catch(_:AiEditFailure){}
            assertEquals(1,transport.posts);assertEquals(1,transport.statuses);assertEquals(0,transport.downloads);assertEquals(0,transport.cancels)
            assertTrue(transport.lastBytes!!.all{it==0.toByte()})
        }
    }
    @Test fun failedRetirementStopsEveryLoopWithExactOriginalError()=runBlocking {
        for(kind in 0..2){
            val release=java.io.IOException("public original retirement failure").apply{addSuppressed(AiNetworkFailure(true))}
            val transport=StatusTransport(releaseFailure=release)
            val actual=try{invoke(kind,transport);null}catch(t:Throwable){t}
            assertSame(release,actual);assertEquals(1,transport.posts);assertEquals(1,transport.statuses);assertEquals(0,transport.downloads);assertEquals(0,transport.cancels)
        }
    }
    private suspend fun exercise(kind:Int,transient:Boolean=false){
        val transport=StatusTransport(transient=transient)
        assertArrayEquals(byteArrayOf(8),invoke(kind,transport));assertEquals(1,transport.posts);assertEquals(if(transient)2 else 1,transport.statuses);assertEquals(1,transport.downloads);assertEquals(0,transport.cancels)
        assertTrue(transport.lastBytes!!.all{it==0.toByte()})
    }
    private suspend fun invoke(kind:Int,transport:AiHttpTransport):ByteArray=when(kind){
        0->ReplicateSeedreamApi(transport,1).edit("synthetic-token".toByteArray(),byteArrayOf(1),"public edit")
        1->ReplicateModelEditApi(transport,1).edit("synthetic-token".toByteArray(),ReplicateEditModel.SEEDREAM,byteArrayOf(1),"public edit",null)
        else->ReplicateImageGenerationApi(transport,1).generate("synthetic-token".toByteArray(),GenerationRequest(GenerationModel.SEEDREAM,"public create",GenerationAspect.SQUARE))
    }
    private class StatusTransport(private val transient:Boolean=false,private val changed:Boolean=false,private val releaseFailure:Throwable?=null):AiHttpTransport {
        var posts=0;var statuses=0;var downloads=0;var cancels=0;var lastBytes:ByteArray?=null
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse=when{
            request.url.endsWith("/cancel")-> {cancels++;json("{}")}
            request.method=="POST"->{posts++;json("{\"id\":\"PublicId42\",\"status\":\"processing\"}")}
            request.url.startsWith("https://replicate.delivery/")->{downloads++;AiHttpResponse(200,"image/png",byteArrayOf(8))}
            else->throw AssertionError("Status reached legacy execute instead of local consumption")
        }
        override suspend fun consumePredictionStatus(expectedId:String,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){
            statuses++;assertEquals("PublicId42",expectedId);assertEquals("https://api.replicate.com/v1/predictions/PublicId42",request.url);assertEquals("GET",request.method);assertNull(request.body);assertEquals(2*1024*1024,request.maxResponseBytes)
            releaseFailure?.let{throw it}
            if(transient&&statuses==1)throw AiNetworkFailure(false)
            val id=if(changed)"OtherId"else expectedId
            val response=json("{\"id\":\"$id\",\"status\":\"succeeded\",\"output\":[\"https://replicate.delivery/public.png\"]}");lastBytes=response.bytes
            try{consume(response)}finally{response.bytes.fill(0)}
        }
        private fun json(value:String)=AiHttpResponse(200,"application/json",value.toByteArray())
    }
}
