package uk.co.traynor.privategallery.core.editor
import org.junit.Assert.*
import org.junit.Test
/** One infrastructure case; never counted as a Native ownership case. */
class Phase3FluxIoProfileTest {
    @Test fun actualWorkerReceivesRequestedSingleIoSetting() {
        if(java.lang.Boolean.getBoolean("privategallery.phase3.requireOneIo")) {
            assertEquals("1",System.getProperty("kotlinx.coroutines.io.parallelism"))
            println("PHASE3 FLUX ACTUAL_WORKER ONE_IO=1")
        }
    }
}
