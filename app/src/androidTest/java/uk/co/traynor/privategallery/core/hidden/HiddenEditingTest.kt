package uk.co.traynor.privategallery.core.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.*

class HiddenEditingTest {
    private fun fixture(test:(android.content.Context,HiddenSession,HiddenRepository,AndroidVaultRepository,ByteArray)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"hidden-editor-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
        }
        val session=HiddenSession {android.os.SystemClock.elapsedRealtime()}
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val key=ByteArray(32){9}
        val bitmap=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val bytes=try {PhotoRenderer.encode(bitmap)} finally {bitmap.recycle()}
        try {test(context,session,HiddenRepository(context,session),AndroidVaultRepository(context,key),bytes)}
        finally {bytes.fill(0);key.fill(0);session.lock();root.deleteRecursively()}
    }
    @Test fun localAndAiCopiesStayHiddenAreDistinctAndPreserveOriginals()=fixture {context,session,hidden,primary,bytes ->
        val parent=hidden.importMedia("original.png","image/png",ByteArrayInputStream(bytes))
        val primaryItem=(primary.importVerified(VaultImportSource("primary.png","image/png",{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
        val before=File(context.noBackupFilesDir,"hidden-media-v1").listFiles()!!.filter {it.name.contains(parent.id)}.associate {it.name to it.readBytes()}
        HiddenEditorAccess(context,session).use {access ->
            val copy=access.saveCopy(parent.id,bytes.copyOf(),null) {false}
            val ai=access.saveCopy(parent.id,bytes.copyOf(),AiEditProvenance(AiProcessing.CLOUD,"replicate","synthetic-model")) {false}
            assertNotEquals(parent.id,copy.id);assertNotEquals(copy.id,ai.id)
            hidden.verify(copy.id);hidden.verify(ai.id)
            assertEquals(MediaOrigin.LOCAL_EDIT,copy.origin);assertEquals(MediaOrigin.REMOTE_AI_EDIT,ai.origin)
            assertTrue(ai.vaultOnly)
            assertTrue(runCatching {access.saveCopy(primaryItem.id,bytes.copyOf(),null) {false}}.isFailure)
            assertTrue(runCatching {access.read(primaryItem.id) {false}}.isFailure)
        }
        assertEquals(3,HiddenRepository(context,session).items().size)
        assertEquals(1,primary.items().size)
        before.forEach {(name,data)->assertArrayEquals(data,File(context.noBackupFilesDir,"hidden-media-v1/$name").readBytes())}
        assertArrayEquals(bytes,hidden.read(parent.id))
        assertTrue(hidden.collections().isEmpty());assertNull(hidden.favouriteCollection())
    }
    @Test fun cancelledClosedAndOldEpochCannotSaveOrInheritReauthentication()=fixture {context,session,hidden,_,bytes ->
        val parent=hidden.importMedia("original.png","image/png",ByteArrayInputStream(bytes))
        val access=HiddenEditorAccess(context,session)
        assertTrue(runCatching {access.saveCopy(parent.id,bytes.copyOf(),null) {true}}.isFailure)
        session.revokeOperations()
        assertTrue(runCatching {access.saveCopy(parent.id,bytes.copyOf(),null) {false}}.isFailure)
        session.lock();session.complete(session.beginAuthentication(),ByteArray(32){7})
        assertFalse(access.isActive())
        assertTrue(runCatching {access.saveGenerated(bytes.copyOf(),"synthetic-model") {false}}.isFailure)
        access.close()
        assertEquals(1,HiddenRepository(context,session).items().size)
        HiddenEditorAccess(context,session).use {fresh ->
            fresh.close();assertTrue(runCatching {fresh.read(parent.id) {false}}.isFailure)
        }
    }
    @Test fun publicationDenialRetainsParentAndPublishesNoCopy()=fixture {context,session,hidden,_,bytes ->
        val parent=hidden.importMedia("original.png","image/png",ByteArrayInputStream(bytes))
        var allowed=true
        HiddenEditorAccess(context,session,authorized={allowed},beforePublication={allowed=false}).use {access ->
            assertTrue(runCatching {access.saveCopy(parent.id,bytes.copyOf(),null) {false}}.isFailure)
        }
        assertEquals(listOf(parent.id),hidden.items().map {it.id})
        hidden.reconcile();assertArrayEquals(bytes,hidden.read(parent.id))
    }
    @Test fun generatedImagesAndRestartedProvenanceRemainHidden()=fixture {context,session,hidden,primary,bytes ->
        val output=bytes.copyOf()
        val item=HiddenEditorAccess(context,session).use {it.saveGenerated(output,"synthetic-model") {false}}
        assertTrue(output.all {it==0.toByte()})
        val reopened=HiddenRepository(context,session).items().single()
        assertEquals(item.id,reopened.id);assertEquals(MediaOrigin.REMOTE_AI_GENERATED,reopened.origin)
        assertTrue(reopened.vaultOnly);assertTrue(primary.items().isEmpty())
        assertArrayEquals(bytes,hidden.read(item.id))
    }
    @Test fun legacyMetadataWithoutEditorFieldsRemainsEditable()=fixture {context,session,hidden,_,bytes ->
        val parent=hidden.importMedia("original.png","image/png",ByteArrayInputStream(bytes))
        val records=HiddenRecordStore(File(context.noBackupFilesDir,"hidden-media-v1"))
        val id="00000000-0000-0000-0000-000000000000"
        val key=ByteArray(32){7}
        val encoded=records.read("metadata",id,key)
        val json=org.json.JSONObject(encoded.toString(Charsets.UTF_8));encoded.fill(0)
        json.getJSONArray("items").getJSONObject(0).apply {remove("origin");remove("vaultOnly");remove("source")}
        records.promote(records.stage("metadata",id,json.toString().toByteArray(),key),"metadata",id)
        records.syncPromotions();key.fill(0)
        HiddenEditorAccess(context,session).use {access ->
            val copy=access.saveCopy(parent.id,bytes.copyOf(),null) {false}
            assertEquals(MediaOrigin.LOCAL_EDIT,copy.origin)
        }
        assertArrayEquals(bytes,hidden.read(parent.id))
    }
    @Test fun primaryPublicationDenialKeepsOriginalAndHiddenUntouched()=fixture {_,_,hidden,primary,bytes ->
        val parent=(primary.importVerified(VaultImportSource("primary.png","image/png",{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
        assertTrue(runCatching {primary.importAiEditedCopy(parent.id,bytes,null,true,publishIfAllowed={error("Revoked")}) {false}}.isFailure)
        assertEquals(listOf(parent.id),primary.items().map {it.id});assertTrue(hidden.items().isEmpty())
        val loaded=primary.readForEditing(parent) {false}
        try {assertArrayEquals(bytes,loaded)} finally {loaded.fill(0)}
    }
    @Test fun fullEditorReadAboveSixteenMiBIsBoundedAndNotAThumbnail()=fixture {context,session,hidden,_,bytes ->
        val source=ByteArray(20*1024*1024);bytes.copyInto(source)
        val parent=hidden.importMedia("large.png","image/png",ByteArrayInputStream(source))
        HiddenEditorAccess(context,session).use {access ->
            val loaded=access.read(parent.id) {false}
            try {assertArrayEquals(source,loaded)} finally {loaded.fill(0)}
        }
        source.fill(0)
    }
}
