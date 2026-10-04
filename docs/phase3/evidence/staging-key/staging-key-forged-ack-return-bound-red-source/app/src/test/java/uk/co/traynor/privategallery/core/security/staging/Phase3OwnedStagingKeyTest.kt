package uk.co.traynor.privategallery.core.security.staging

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit
import org.bouncycastle.crypto.InvalidCipherTextException
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Public plaintext fixtures and actual host-JNI/kernel entropy once integrated.
 * Reflection observes exact known arrays/borrow metadata only; no owner data or key logs.
 * The held-borrow case models Java acknowledgement, not a blocked JNI invocation. */
class Phase3OwnedStagingKeyTest {
    private class Fixture(val owned:OwnedResource<OwnedEphemeralStagingKey>,val plain:OwnedByteBuffer,
        val cipher:OwnedNativeOutputBuffer):AutoCloseable {
        val key get()=owned.value
        override fun close(){owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS));assertFalse(owned.releaseFailed)}
    }
    private fun fixture(guard:ScopedIoGuard,total:Long=31):Fixture {
        var plain:OwnedByteBuffer?=null;var cipher:OwnedNativeOutputBuffer?=null
        val owned=guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")) {
            val key=stagingKey("key",guard,StagingChunkLayout(total))
            plain=create("plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(guard,original,1,65536)}.value
            cipher=nativeOutputBuffer("cipher",guard,65552).value
            key
        }
        return try{Fixture(owned,checkNotNull(plain),checkNotNull(cipher))}catch(t:Throwable){owned.close();throw t}
    }
    private inline fun isolated(block:(PrimarySessionAuthority,PrimaryOperation,ScopedIoGuard)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32){11});val owner=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try{block(authority,owner,ScopedIoGuard(owner,PrimaryScope.READ))}finally{owner.close();authority.revoke()}
    }
    private inline fun isolatedReturns(block:(PrimarySessionAuthority,ScopedIoGuard,ReleasePool,java.util.concurrent.CountDownLatch)->Unit) {
        val returned=java.util.concurrent.CountDownLatch(3)
        // TEST ONLY: notification AFTER each actual action and actual accounting callback returned.
        val pool=ReleasePool(16,allocateInvocation={action,finished->ReleaseInvocation(action){finished();returned.countDown()}})
        val authority=testPrimaryAuthority();authority.javaClass.getDeclaredField("ioReleasePool").apply{isAccessible=true}.set(authority,pool)
        authority.open(ByteArray(32){11});val owner=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try{block(authority,ScopedIoGuard(owner,PrimaryScope.READ),pool,returned)}finally{owner.close();authority.revoke()}
    }
    private fun keyBytes(key:OwnedEphemeralStagingKey)=key.javaClass.getDeclaredField("keyBytes").apply{isAccessible=true}.get(key) as ByteArray
    private fun bytes(plain:OwnedByteBuffer):ByteArray {var out:ByteArray?=null;plain.useBytes{out=it;true};return checkNotNull(out)}
    private fun bytes(cipher:OwnedNativeOutputBuffer):ByteArray {var out:ByteArray?=null;cipher.useBytes{out=it;0};return checkNotNull(out)}
    private fun fill(plain:OwnedByteBuffer,value:Int){plain.useBytes{it.fill(value.toByte());true}}
    private fun restore(cipher:OwnedNativeOutputBuffer,saved:ByteArray){cipher.useBytes{it.fill(0);saved.copyInto(it);saved.size}}
    private fun sealed(f:Fixture,index:Long,length:Int):ByteArray {fill(f.plain,37);assertEquals(length+16,f.key.seal(index,length,f.plain,f.cipher));return bytes(f.cipher).copyOf(length+16)}
    private fun reflect(key:OwnedEphemeralStagingKey,name:String,vararg args:Any) {
        val types=args.map{when(it){is Boolean->Boolean::class.javaPrimitiveType!!;is Long->Long::class.javaPrimitiveType!!;else->error("Public reflection fixture type")}}.toTypedArray()
        try{key.javaClass.getDeclaredMethod(name,*types).apply{isAccessible=true}.invoke(key,*args)}catch(t:InvocationTargetException){throw checkNotNull(t.cause)}
    }
    @Test fun missingReadCapabilityDeniesBeforeFactory() {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32){11});val owner=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.LOCAL_EDIT)));var ran=false
        try{val guard=ScopedIoGuard(owner,PrimaryScope.READ);assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")){ran=true;stagingKey("key",guard,StagingChunkLayout(1))}};assertFalse(ran)}finally{owner.close();authority.revoke()}
    }
    @Test fun completeThreeChildQuotaDenialDoesNotCreateKeyOrInvokeFactory()=isolated{_,_,guard->
        val holds=List(14){guard.createOwned(OwnedResourceManifest.io("hold")){attach("hold",AutoCloseable{})}};var ran=false
        try{assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")){ran=true;stagingKey("key",guard,StagingChunkLayout(1))}};assertFalse(ran)}finally{holds.forEach{it.close()};holds.forEach{assertTrue(it.retirement.await(5,TimeUnit.SECONDS))}}
        fixture(guard,1).use{assertEquals(17,it.key.seal(0,1,it.plain,it.cipher))}
    }
    @Test fun presentationPartitionCannotConstructEphemeralKey()=isolated{_,_,guard->
        assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.presentation("key","plain","cipher")){stagingKey("key",guard,StagingChunkLayout(1))}}
    }
    @Test fun anotherOperationCannotConstructKeyInAnOriginal()=isolated{authority,_,guard->
        checkNotNull(authority.operationOrNull()).use{other->val foreign=ScopedIoGuard(other,PrimaryScope.READ)
            assertThrows(IllegalStateException::class.java){guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")){stagingKey("key",foreign,StagingChunkLayout(1))}}}
    }
    @Test fun duplicateKeyClaimDeniesBeforeCreatingAnotherKeyAndOriginalContinues()=isolated{_,_,guard->
        var plain:OwnedByteBuffer?=null;var cipher:OwnedNativeOutputBuffer?=null
        val owned=guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")){
            val key=stagingKey("key",guard,StagingChunkLayout(1));assertThrows(IllegalStateException::class.java){stagingKey("key",guard,StagingChunkLayout(1))}
            plain=create("plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(guard,original,1,65536)}.value;cipher=nativeOutputBuffer("cipher",guard,65552).value;key
        };Fixture(owned,checkNotNull(plain),checkNotNull(cipher)).use{assertEquals(17,it.key.seal(0,1,it.plain,it.cipher))}
    }
    @Test fun constructorCannotUseKeyBeforeCompletedOriginalAndDenialDoesNotConsume()=isolated{_,_,guard->
        var plain:OwnedByteBuffer?=null;var cipher:OwnedNativeOutputBuffer?=null
        val owned=guard.createOwned(OwnedResourceManifest.io("key","plain","cipher")){
            val key=stagingKey("key",guard,StagingChunkLayout(1));plain=create("plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(guard,original,1,65536)}.value;cipher=nativeOutputBuffer("cipher",guard,65552).value
            assertThrows(IllegalStateException::class.java){key.seal(0,1,checkNotNull(plain),checkNotNull(cipher))};key
        };Fixture(owned,checkNotNull(plain),checkNotNull(cipher)).use{assertEquals(17,it.key.seal(0,1,it.plain,it.cipher))}
    }
    @Test fun actualFreshEntropyTargetsAreExactKnownArraysAndRetireToZero()=isolated{_,_,guard->
        val a=fixture(guard);val b=fixture(guard);val ka=keyBytes(a.key);val kb=keyBytes(b.key)
        try{assertEquals(32,ka.size);assertEquals(32,kb.size);assertTrue(ka.any{it!=0.toByte()});assertTrue(kb.any{it!=0.toByte()});assertFalse(ka.contentEquals(kb))}finally{a.close();b.close()}
        assertTrue("Known first key not wiped",ka.all{it==0.toByte()});assertTrue("Known second key not wiped",kb.all{it==0.toByte()})
    }
    @Test fun fullAndPartialSealsClearEntirePlainWorkspaceAndOpenExactChunks()=isolated{_,_,guard->
        fixture(guard,65537).use{f->val first=sealed(f,0,65536);val last=sealed(f,1,1)
            try{assertArrayEquals(ByteArray(65536),bytes(f.plain));assertTrue(bytes(f.cipher).drop(17).all{it==0.toByte()})
                restore(f.cipher,first);assertEquals(65536,f.key.open(0,65552,f.cipher,f.plain));assertTrue(bytes(f.plain).all{it==37.toByte()})
                restore(f.cipher,last);assertEquals(1,f.key.open(1,17,f.cipher,f.plain));assertEquals(37.toByte(),bytes(f.plain)[0]);assertTrue(bytes(f.plain).drop(1).all{it==0.toByte()})
            }finally{first.fill(0);last.fill(0)}}
    }
    @Test fun openingBeforeWholeFamilySealedDeniesWithoutPoisonOrBufferMutation()=isolated{_,_,guard->
        fixture(guard,65537).use{f->val first=sealed(f,0,65536)
            try{fill(f.plain,9);val output=bytes(f.plain);val input=bytes(f.cipher);assertThrows(IllegalStateException::class.java){f.key.open(0,65552,f.cipher,f.plain)};assertTrue(output.all{it==9.toByte()});assertTrue(first.contentEquals(input.copyOf(first.size)));val last=sealed(f,1,1);last.fill(0)
                restore(f.cipher,first);assertEquals(65536,f.key.open(0,65552,f.cipher,f.plain))}finally{first.fill(0)}}
    }
    @Test fun readonlyRepeatedOpenDoesNotReissueSealingNonce()=isolated{_,_,guard->
        fixture(guard).use{f->val saved=sealed(f,0,31)
            try{repeat(2){restore(f.cipher,saved);assertEquals(31,f.key.open(0,47,f.cipher,f.plain));assertTrue(bytes(f.plain).take(31).all{it==37.toByte()})};assertThrows(IllegalStateException::class.java){f.key.seal(0,31,f.plain,f.cipher)}}finally{saved.fill(0)}}
    }
    @Test fun everyCipherAndTagByteFailureWipesKnownKeyAndBothWorkspacesAndRetiresFamily()=isolated{_,_,guard->
        for(position in 0 until 47){val f=fixture(guard);val saved=sealed(f,0,31);val key=keyBytes(f.key);val plain=bytes(f.plain);val cipher=bytes(f.cipher)
            try{saved[position]=(saved[position].toInt() xor 1).toByte();restore(f.cipher,saved);fill(f.plain,9)
                assertThrows(InvalidCipherTextException::class.java){f.key.open(0,47,f.cipher,f.plain)};assertTrue("Known key not wiped",key.all{it==0.toByte()});assertArrayEquals(ByteArray(65536),plain);assertArrayEquals(ByteArray(65552),cipher)
                assertThrows(IllegalStateException::class.java){f.key.open(0,47,f.cipher,f.plain)};assertThrows(IllegalStateException::class.java){f.key.seal(0,31,f.plain,f.cipher)}
            }finally{saved.fill(0);f.close()}}
    }
    @Test fun badIndexLengthAndRepeatedClaimPreflightLeaveKeyAndBuffersUsable()=isolated{_,_,guard->
        fixture(guard,65537).use{f->fill(f.plain,9);val plain=bytes(f.plain);val cipher=bytes(f.cipher);cipher.fill(7)
            for(n in intArrayOf(-1,0,65535,65537,Int.MAX_VALUE))assertThrows(IllegalArgumentException::class.java){f.key.seal(0,n,f.plain,f.cipher)}
            assertThrows(IllegalArgumentException::class.java){f.key.seal(-1,65536,f.plain,f.cipher)};assertTrue(plain.all{it==9.toByte()});assertTrue(cipher.all{it==7.toByte()})
            val first=sealed(f,0,65536);try{assertThrows(IllegalStateException::class.java){f.key.seal(0,65536,f.plain,f.cipher)};val last=sealed(f,1,1);last.fill(0)}finally{first.fill(0)}}
    }
    @Test fun foreignSameOwnerOriginalBuffersDenyBeforeEitherBufferIsBorrowed()=isolated{_,_,guard->
        fixture(guard).use{a->fixture(guard).use{b->fill(b.plain,9);val foreignPlain=bytes(b.plain);val foreignCipher=bytes(b.cipher);foreignCipher.fill(7)
            assertThrows(IllegalStateException::class.java){a.key.seal(0,31,b.plain,b.cipher)};assertTrue(foreignPlain.all{it==9.toByte()});assertTrue(foreignCipher.all{it==7.toByte()});val saved=sealed(a,0,31);saved.fill(0)}}
    }
    @Test fun sameOriginalDifferentGuardBuffersDenyWithoutConsumingFamily()=isolated{_,owner,guard->
        val other=ScopedIoGuard(owner,PrimaryScope.READ);var goodPlain:OwnedByteBuffer?=null;var badPlain:OwnedByteBuffer?=null;var goodCipher:OwnedNativeOutputBuffer?=null;var badCipher:OwnedNativeOutputBuffer?=null
        val owned=guard.createOwned(OwnedResourceManifest.io("key","plain","cipher","other-plain","other-cipher")){
            val key=stagingKey("key",guard,StagingChunkLayout(1));goodPlain=create("plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(guard,original,1,65536)}.value;goodCipher=nativeOutputBuffer("cipher",guard,65552).value
            badPlain=create("other-plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(other,original,3,65536)}.value;badCipher=nativeOutputBuffer("other-cipher",other,65552).value;key
        }
        try{fill(checkNotNull(badPlain),9);val p=bytes(checkNotNull(badPlain));val c=bytes(checkNotNull(badCipher));c.fill(7);assertThrows(IllegalStateException::class.java){owned.value.seal(0,1,checkNotNull(badPlain),checkNotNull(badCipher))};assertTrue(p.all{it==9.toByte()});assertTrue(c.all{it==7.toByte()});assertEquals(17,owned.value.seal(0,1,checkNotNull(goodPlain),checkNotNull(goodCipher)))}finally{owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun wrongWorkspaceSizesDenyWithoutConsumingFamily()=isolated{_,_,guard->
        var plain:OwnedByteBuffer?=null;var cipher:OwnedNativeOutputBuffer?=null;var bad:OwnedNativeOutputBuffer?=null
        val owned=guard.createOwned(OwnedResourceManifest.io("key","plain","cipher","bad")){
            val key=stagingKey("key",guard,StagingChunkLayout(1));plain=create("plain",{it:OwnedByteBuffer->it.close()}){OwnedByteBuffer(guard,original,1,65536)}.value;cipher=nativeOutputBuffer("cipher",guard,65552).value;bad=nativeOutputBuffer("bad",guard,32).value;key
        }
        try{val b=bytes(checkNotNull(bad));b.fill(7);assertThrows(IllegalArgumentException::class.java){owned.value.seal(0,1,checkNotNull(plain),checkNotNull(bad))};assertTrue(b.all{it==7.toByte()});assertEquals(17,owned.value.seal(0,1,checkNotNull(plain),checkNotNull(cipher)))}finally{owned.close();assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))}
    }
    @Test fun explicitRetirementWipesKnownKeyAndDoesNotBorrowBuffersAgain()=isolated{_,_,guard->
        fixture(guard).use{f->val key=keyBytes(f.key);fill(f.plain,9);val plain=bytes(f.plain);val cipher=bytes(f.cipher);cipher.fill(7)
            assertTrue(f.key.closeAcknowledged().toCompletableFuture().isDone);assertTrue("Known key not wiped",key.all{it==0.toByte()});assertThrows(IllegalStateException::class.java){f.key.seal(0,31,f.plain,f.cipher)};assertTrue(plain.all{it==9.toByte()});assertTrue(cipher.all{it==7.toByte()})}
    }
    @Test fun heldJavaBorrowKeepsPendingAcknowledgementAndBlocksFreshAuthUntilActualLeave()=isolated{authority,_,guard->
        val f=fixture(guard);val key=keyBytes(f.key);reflect(f.key,"enter",true,0L)
        try{val stage=f.key.closeAcknowledged().toCompletableFuture();assertFalse(stage.isDone);assertTrue(key.any{it!=0.toByte()});authority.revoke();assertFalse(authority.cleanupComplete);assertThrows(IllegalStateException::class.java){authority.open(ByteArray(32){12})}
        }finally{reflect(f.key,"leave");f.close()};assertTrue("Known key not wiped",key.all{it==0.toByte()});assertTrue(authority.cleanupComplete);authority.open(ByteArray(32){13});assertThrows(IllegalStateException::class.java){f.key.seal(0,31,f.plain,f.cipher)}
    }
    @Test fun callerCompletedAcknowledgementSnapshotCannotReturnHeldRootSlot()=isolatedReturns{_,guard,pool,returned->
        val f=fixture(guard);val key=keyBytes(f.key);reflect(f.key,"enter",true,0L)
        try{val snapshot=f.key.closeAcknowledged().toCompletableFuture();assertTrue(snapshot.complete(Unit));f.owned.close()
            assertTrue("Actual release callbacks did not return",returned.await(5,TimeUnit.SECONDS))
            assertEquals("Held root physical slot returned early",1,pool.occupied)
            assertFalse("Held root was acknowledged by a caller-completed snapshot",f.owned.retirement.isComplete)
            assertTrue("Held key unexpectedly wiped before actual leave",key.any{it!=0.toByte()})
        }finally{reflect(f.key,"leave");f.close()};assertTrue("Known key not wiped",key.all{it==0.toByte()})
    }
    @Test fun snapshotCancellationFailureAndObtrusionCannotPoisonActualRootAcknowledgement()=isolatedReturns{authority,guard,pool,returned->
        val f=fixture(guard);val key=keyBytes(f.key);reflect(f.key,"enter",true,0L)
        try{val stage=f.key.closeAcknowledged();assertFalse(stage is java.util.concurrent.CompletableFuture<*>)
            assertTrue(stage.toCompletableFuture().cancel(true))
            assertTrue(stage.toCompletableFuture().completeExceptionally(IllegalStateException("Public snapshot fixture")))
            stage.toCompletableFuture().obtrudeValue(Unit)
            stage.toCompletableFuture().obtrudeException(IllegalStateException("Public snapshot fixture"))
            assertFalse(stage.toCompletableFuture().isDone);f.owned.close();authority.revoke()
            assertTrue(returned.await(5,TimeUnit.SECONDS));assertEquals(1,pool.occupied);assertFalse(f.owned.retirement.isComplete);assertFalse(authority.cleanupComplete)
            assertThrows(IllegalStateException::class.java){authority.open(ByteArray(32){12})}
        }finally{reflect(f.key,"leave");f.close()};assertTrue("Known key not wiped",key.all{it==0.toByte()});assertTrue(authority.cleanupComplete)
    }
    @Test fun callerCompletedDerivedStagesCannotCompleteThePrivateAcknowledgementCell()=isolatedReturns{_,guard,pool,returned->
        val f=fixture(guard);reflect(f.key,"enter",true,0L)
        try{val stage=f.key.closeAcknowledged();val done=java.util.concurrent.CompletableFuture.completedFuture(Unit)
            val derived=listOf(stage.thenApply{it},stage.handle{_,_->Unit},stage.exceptionally{Unit},
                stage.thenCompose{done},stage.whenComplete{_,_->},stage.thenCombine(done){_,_->Unit})
            for(child in derived){assertTrue(child.toCompletableFuture().complete(Unit));assertFalse(stage.toCompletableFuture().isDone)}
            f.owned.close();assertTrue(returned.await(5,TimeUnit.SECONDS));assertEquals(1,pool.occupied);assertFalse(f.owned.retirement.isComplete)
        }finally{reflect(f.key,"leave");f.close()}
    }
    @Test fun authorityGatedEntryRejectsBeforeAnyNativeEntropyAcquisition()=isolated{_,_,guard->
        assertThrows(IllegalStateException::class.java){guard.commit{fixture(guard,1)}};fixture(guard,1).use{assertEquals(17,it.key.seal(0,1,it.plain,it.cipher))}
    }
    @Test fun invalidOpenLengthPreflightDoesNotRetireOrWipeAnOtherwiseSealedFamily()=isolated{_,_,guard->
        fixture(guard).use{f->val saved=sealed(f,0,31);try{fill(f.plain,9);val p=bytes(f.plain);val c=bytes(f.cipher)
            for(n in intArrayOf(-1,0,15,16,46,48,Int.MAX_VALUE))assertThrows(IllegalArgumentException::class.java){f.key.open(0,n,f.cipher,f.plain)}
            assertTrue(p.all{it==9.toByte()});assertTrue(saved.contentEquals(c.copyOf(47)));restore(f.cipher,saved);assertEquals(31,f.key.open(0,47,f.cipher,f.plain))}finally{saved.fill(0)}}
    }
}
