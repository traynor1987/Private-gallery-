package uk.co.traynor.privategallery.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import uk.co.traynor.privategallery.CollectionManagerDialog
import uk.co.traynor.privategallery.core.vault.VaultCollection
import java.time.*
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess
import uk.co.traynor.privategallery.core.hidden.TransferJournal
import uk.co.traynor.privategallery.core.media.VaultStorageSummary
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Actual previously unmapped app components, with synthetic data and isolated storage only.
 * Test definitions are not physical Samsung, TalkBack or fold-transition acceptance. */
class ModalCompletion1762Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { name ->
        if (name.contains("Landscape", ignoreCase = true)) 840 to 392 else if (name.contains("Inner", ignoreCase = true)) 840 to 900 else 320 to 640
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    private fun content(theme: AppTheme, body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PrivateGalleryTheme(theme) { body() }
            }
        }
    }
    private fun fixtureRoot() = File(compose.activity.noBackupFilesDir, "modal-completion-${UUID.randomUUID()}").apply { mkdirs() }
    private fun isolated(root: File): Context = object : ContextWrapper(compose.activity) {
        override fun getNoBackupFilesDir() = File(root, "hidden").apply { mkdirs() }
        override fun getFilesDir() = File(root, "primary").apply { mkdirs() }
    }
    private fun waitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun coverDarkStorageLongContentAndCacheConsentRemainReachable() {
        val cacheCalls = AtomicInteger()
        val media = (0 until 10).map {
            VaultItem("item-$it", "image/png", "Synthetic photograph $it with a long descriptive filename.png", 0, 4096,
                ByteArray(32), ByteArray(12), VaultItemState.COMPLETE)
        }
        val summary = VaultStorageSummary(100_000, 80_000, 0, 10_000, 5_000, 5_000, 10, 0, media, listOf(media))
        content(AppTheme.DARK) {
            VaultStoragePanel(load = { it(Result.success(summary)) }, clearCache = { callback ->
                cacheCalls.incrementAndGet(); callback(Result.success(123L))
            })
        }
        compose.onNodeWithText("Storage").performClick()
        compose.onNodeWithText("Close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Duplicate candidates use recorded size and SHA-256.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Clear disposable preview cache").performScrollTo().performClick()
        compose.onNodeWithText("Clear disposable previews?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, cacheCalls.get()) }
        compose.onNodeWithText("Clear disposable preview cache").performScrollTo().performClick()
        compose.onNodeWithText("Clear previews").performClick()
        waitText("Freed 123 bytes")
        compose.runOnIdle { assertEquals(1, cacheCalls.get()) }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Vault storage").assertDoesNotExist()
    }
    @Test fun landscapeLightRetiredModelConsentCancelsBeforeIsolatedCleanup() {
        val root = fixtureRoot()
        val context = isolated(root)
        val model = File(context.noBackupFilesDir, "ai-models/sd15-fp16-v1.part").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(17) { 5 }) }
        val unrelated = File(context.noBackupFilesDir, "retained-vault-record.enc").apply { writeBytes(byteArrayOf(7, 11, 13)) }
        try {
            content(AppTheme.LIGHT) { CompositionLocalProvider(LocalContext provides context) { RetiredModelCleanup() } }
            compose.onNodeWithText("Remove downloaded AI models ·", substring = true).performClick()
            compose.onNodeWithText("Remove downloaded AI models?").assertIsDisplayed()
            compose.onNodeWithText("Remove models").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertTrue(model.isFile); assertArrayEquals(byteArrayOf(7, 11, 13), unrelated.readBytes()) }
            compose.onNodeWithText("Remove downloaded AI models ·", substring = true).performClick()
            compose.onNodeWithText("Remove models").performClick()
            compose.waitUntil(10_000) { !model.exists() }
            compose.runOnIdle { assertArrayEquals(byteArrayOf(7, 11, 13), unrelated.readBytes()) }
        } finally { root.deleteRecursively() }
    }
    @Test fun coverLightPanicTestConfirmationCancelsWithoutChangingSettings() {
        val changes = AtomicInteger()
        content(AppTheme.LIGHT) {
            Column(Modifier.verticalScroll(rememberScrollState())) { PrivacyShieldSettings { changes.incrementAndGet() } }
        }
        compose.onNodeWithText("Test Panic Exit safely").performScrollTo().performClick()
        compose.onNodeWithText("Test Panic Exit?").assertIsDisplayed()
        compose.onNodeWithText("Lock and test").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Test Panic Exit?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, changes.get()) }
    }
    @Test fun landscapeDarkSourcePickerCanReachLastOfLargeVaultAtLargeText() {
        val active = AtomicBoolean(true)
        val candidates = (0 until 300).map { "synthetic-$it" }
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } } finally { bitmap.recycle() }
        var chosen: String? = null
        try {
            content(AppTheme.DARK) {
                var open by remember { mutableStateOf(true) }
                if (open) AiSourcePicker(2, "Hidden Vault", candidates, active,
                    load = { _, done -> done(Result.success(bytes.copyOf())) }, loadForEditing = null,
                    onSelect = { chosen = it; open = false }, onDismiss = { open = false })
            }
            compose.onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(299)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("ai-candidate-thumbnail-synthetic-299").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("ai-candidate-synthetic-299").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("synthetic-299", chosen) }
        } finally { active.set(false); bytes.fill(0) }
    }
    @Test fun coverLightBrowsingDataConfirmationRequiresExplicitChoiceAtLargeText() {
        val clears = AtomicInteger()
        content(AppTheme.LIGHT) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BrowserSettingsContent(BrowserSearchEngine.DUCKDUCKGO, {}, true, {}, true, {}, false, {}, true, {}, { clears.incrementAndGet() })
            }
        }
        compose.onNodeWithText("Clear browsing data").performScrollTo().performClick()
        compose.onNodeWithText("Clear browsing data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, clears.get()) }
        compose.onNodeWithText("Clear browsing data").performScrollTo().performClick()
        compose.onNodeWithText("Clear").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clears.get()) }
        compose.onNodeWithText("Browsing data cleared.").performScrollTo().assertIsDisplayed()
    }
    @Test fun coverDarkCreateImageChoicesReferencesDiagnosticsAndConsentFitAtLargeText() {
        val root = fixtureRoot()
        val preferences = mutableSetOf<String>()
        val context = object : ContextWrapper(isolated(root)) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val scoped = "${root.name}-$name"; preferences += scoped
                return compose.activity.getSharedPreferences(scoped, mode)
            }
        }
        GenerationModelStore(context).select(GenerationModel.SEEDREAM_5_PRO)
        AiCredentialStore(context).save(ReplicateSeedreamProvider.ID, byteArrayOf(1, 2, 3))
        val requests = AtomicInteger()
        val media = (0 until 300).map { VaultItem("ref-$it", "image/png", "Synthetic reference $it", 0, 10,
            ByteArray(32), ByteArray(12), VaultItemState.COMPLETE) }
        try {
            content(AppTheme.DARK) { CompositionLocalProvider(LocalContext provides context) {
                CreateImageSheet(onGenerate = { _, _, _ -> requests.incrementAndGet(); {} }, onSaved = {}, onDismiss = {}, vaultImages = media)
            } }
            listOf("Square", "Portrait", "Landscape").forEach { label ->
                compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
                compose.onNodeWithText(label).assertIsSelected()
            }
            compose.onNodeWithText("Reference images from Vault ·", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Done").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(300) // Introduction occupies index zero.
            compose.onNodeWithContentDescription("Synthetic reference 299").assertIsDisplayed().performClick()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Describe the image").performScrollTo().performTextInput("Synthetic fixture prompt")
            compose.onNodeWithText("Show original and effective prompt").performScrollTo().performClick()
            compose.onNodeWithText("Prompt diagnostic").assertIsDisplayed()
            compose.onNodeWithText("Effective prompt").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Generate").performScrollTo().performClick()
            compose.onNodeWithText("Remote image creation").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertEquals(0, requests.get()) }
        } finally {
            root.deleteRecursively()
            preferences.forEach { compose.activity.getSharedPreferences(it, 0).edit().clear().commit() }
        }
    }
    @Test fun coverDarkPromptEditorKeepsActionsVisibleWithActualKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        val oldKeyboard = shell("settings get secure show_ime_with_hard_keyboard")
        val oldFlags = automation.serviceInfo.flags
        val templates = PromptEnhancementStore(compose.activity)
        val original = templates.template(PromptKind.CREATE)
        var dismissed = false
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            content(AppTheme.DARK) { PromptTemplateEditor(PromptKind.CREATE, templates) { dismissed = true } }
            compose.onNodeWithText("Template").performScrollTo().performClick().performTextInput("Synthetic unsaved change")
            compose.waitUntil(10_000) { automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            compose.onNodeWithText("Save").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertTrue(dismissed); assertEquals(original, templates.template(PromptKind.CREATE)) }
        } finally {
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
            if (oldKeyboard == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else { require(oldKeyboard.matches(Regex("[01]"))); shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard") }
        }
    }
    @Test fun coverLightCollectionActionsScrollWhileFooterStaysReachable() {
        val favourite = AtomicInteger(); val cover = AtomicInteger(); val pin = AtomicInteger(); val deleted = AtomicInteger()
        content(AppTheme.LIGHT) {
            var open by remember { mutableStateOf(true) }
            if (open) CollectionManagerDialog(VaultCollection("synthetic", "Synthetic long collection name for a narrow window", 0),
                onRename = {}, onDelete = { deleted.incrementAndGet() }, onSetFavourite = { favourite.incrementAndGet() },
                onDismiss = { open = false }, onCover = { cover.incrementAndGet() }, onPin = { pin.incrementAndGet() })
        }
        listOf("Set favourite", "Change cover", "Pin collection").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            compose.onNodeWithText("Save").assertIsDisplayed()
        }
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete collection").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, favourite.get()); assertEquals(1, cover.get()); assertEquals(1, pin.get()); assertEquals(0, deleted.get()) }
    }
    @Test fun coverDarkSizeSelectionPresetsWrapAndKeepLastChoiceUsable() {
        val media = listOf(10L, 200_000_000L, 600_000_000L, 2_000_000_000L).mapIndexed { index, size ->
            VaultItem("size-$index", "image/png", "Synthetic size $index", 0, size, ByteArray(32), ByteArray(12), VaultItemState.COMPLETE)
        }
        var selected: Set<String>? = null
        content(AppTheme.DARK) { MediaSelectionDialog(media, "Hidden Vault", {}, { selected = it }) }
        compose.onNodeWithText("File size").performScrollTo().performClick()
        listOf("100 MB+", "500 MB+", "Over 1 GB").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).performClick()
        }
        compose.onNodeWithText("Select matches").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(setOf("size-3"), selected) }
    }
    @Test fun coverDarkDateTimeFieldsScaleAndKeyboardDoneCommitsOnce() {
        val initial = ZonedDateTime.of(2024, 6, 6, 0, 0, 0, 0, ZoneOffset.UTC)
        var result: Triple<LocalDate, Int, Int>? = null
        val commits = AtomicInteger()
        content(AppTheme.DARK) {
            var open by remember { mutableStateOf(true) }
            if (open) MediaDisplayDateTimeDialog(initial, onDismiss = { open = false }, onSelect = { date, hour, minute ->
                commits.incrementAndGet(); result = Triple(date, hour, minute); open = false
            })
        }
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("media-hour").performScrollTo().performTextReplacement("99")
        compose.onNodeWithText("Save date").assertIsNotEnabled()
        compose.onNodeWithTag("media-hour").performScrollTo().performTextReplacement("13")
        compose.onNodeWithTag("media-minute").performScrollTo().performTextReplacement("42")
        compose.onNodeWithTag("media-minute").performImeAction()
        compose.runOnIdle { assertEquals(1, commits.get()); assertEquals(Triple(LocalDate.of(2024, 6, 6), 13, 42), result) }
    }
    @Test fun landscapeLightDstGapCannotChangeOriginalMetadataOrMediaHash() {
        val original = VaultItem("dst", "image/png", "Synthetic DST fixture.png", 0, 1, ByteArray(32) { 19 }, ByteArray(12),
            VaultItemState.COMPLETE, capturedAtEpochMillis = Instant.parse("2024-03-31T00:00:00Z").toEpochMilli(), adjustedTimeZone = "Europe/London")
        val changes = AtomicInteger()
        content(AppTheme.LIGHT) {
            MediaDetailsDialog("dst", "Hidden Vault", { _, done -> done(Result.success(original)) },
                { _, _, _, done -> changes.incrementAndGet(); done(Result.success(Unit)) }, null, {}, {})
        }
        compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
        compose.onNodeWithText("Next").assertIsDisplayed().performClick()
        compose.onNodeWithTag("media-hour").performScrollTo().performTextReplacement("01")
        compose.onNodeWithTag("media-minute").performScrollTo().performTextReplacement("30")
        compose.onNodeWithText("Save date").assertIsDisplayed().performClick()
        compose.onNodeWithText("This date/time is invalid, missing or ambiguous", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, changes.get()); assertNull(original.adjustedTakenAtEpochMillis); assertArrayEquals(ByteArray(32) { 19 }, original.plaintextSha256) }
    }
    @Test fun innerDarkEncryptedTransferHistoryScrollsAndCancelsOnlyRecordedOperation() {
        val root = fixtureRoot()
        val context = isolated(root)
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        val key = ByteArray(32) { 41 }
        assertTrue(session.complete(session.beginAuthentication(), key.copyOf()))
        val journal = TransferJournal(File(context.noBackupFilesDir, "hidden-media-v1/transfers-v1"), key) { it.run() }
        val interrupted = journal.create("PRIMARY_TO_HIDDEN", false, listOf("synthetic-source"))
        interrupted.status = "INTERRUPTED"; journal.save(interrupted)
        Thread.sleep(2) // Keep the oldest fixture distinct from the following wall-clock journal entries.
        repeat(29) { index ->
            val operation = journal.create("PRIMARY_TO_HIDDEN", false, (0..index + 1).map { "synthetic-$it" })
            operation.status = "COMPLETE"; operation.ended = System.currentTimeMillis(); journal.save(operation)
        }
        val access = PrimaryCopyAccess(context, session).apply { authorize(ByteArray(32) { 43 }) }
        val retained = File(context.noBackupFilesDir, "retained-media.enc").apply { writeBytes(byteArrayOf(2, 3, 5)) }
        try {
            content(AppTheme.DARK) {
                var open by remember { mutableStateOf(true) }
                if (open) TransferHistoryDialog(access) { open = false }
            }
            waitText("History is encrypted")
            compose.onNodeWithText("Close").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
            compose.onNodeWithText("Cancel transfer").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { journal.load(interrupted.id).status == "CANCELLED" }
            compose.runOnIdle { assertArrayEquals(byteArrayOf(2, 3, 5), retained.readBytes()) }
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Transfer history").assertDoesNotExist()
        } finally { access.close(); session.lock(); key.fill(0); root.deleteRecursively() }
    }
}
