package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

/** Buffers never enter saved-instance state. Backgrounding cancels and discards edits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoEditor(
    id: String,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    initialCrop: NormalizedCrop? = null,
    onCancel: () -> Unit,
    onSave: (ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit,
    onSaveRemote: ((ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    provider: AiImageEditProvider? = AiProviderRegistry.selected,
    loadForEditing: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)? = null,
    onSaveAi: ((ByteArray, AiEditProvenance, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    vaultLabel: String = "Vault",
    combineCandidates: List<String> = emptyList(),
    onSaveWithSource: ((String, ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    onSaveRemoteWithSource: ((String, ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    onExternalEdit: ((String)->Unit)? = null,
    onSaveAiWithSource: ((String, ByteArray, AiEditProvenance, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val consent = remember { AiConsentStore(context) }
    val replicateModels = remember { ReplicateEditModelStore(context) }
    val promptTemplates = remember { PromptEnhancementStore(context) }
    var enhancePrompt by remember { mutableStateOf(promptTemplates.enabled) }
    var promptDiagnostic by remember { mutableStateOf(false) }
    var predictionDiagnostic by remember { mutableStateOf(false) }
    var replicateModel by remember { mutableStateOf(replicateModels.selected()) }
    var proResolution by remember { mutableStateOf(replicateModels.proResolution()) }
    var selectedProvider by remember(provider) { mutableStateOf(provider) }
    var selectedChoice by remember(provider) { mutableStateOf(AiProviderRegistry.choice) }
    val currentProvider = selectedProvider
    val providerConfigured = currentProvider?.configured == true
    var history by remember(id) { mutableStateOf(EditHistory(PhotoEdit(crop = initialCrop ?: NormalizedCrop.ORIGINAL))) }
    var draft by remember(id) { mutableStateOf(history.current) }
    var tool by remember { mutableStateOf("Crop") }
    var chosenSourceId by remember(id) { mutableStateOf(id) }
    var editorVisible by remember { mutableStateOf(true) }
    var sourceLoadRevision by remember(id) { mutableIntStateOf(0) }
    var referenceLoadRevision by remember(id) { mutableIntStateOf(0) }
    var sourceLoading by remember(id) { mutableStateOf(true) }
    var referenceLoading by remember(id) { mutableStateOf(false) }
    var source by remember(id) { mutableStateOf<ByteArray?>(null) }
    var preview by remember(id) { mutableStateOf<Bitmap?>(null) }
    var resultPreview by remember(id) { mutableStateOf<Bitmap?>(null) }
    var resultProvenance by remember(id) { mutableStateOf<AiEditProvenance?>(null) }
    var cloudResult by remember(id) { mutableStateOf<ByteArray?>(null) }
    val aiResult = cloudResult
    var otherBusy by remember { mutableStateOf(false) }
    var generationStart by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val providerProgress by (currentProvider?.progress?.collectAsState() ?: remember { mutableStateOf<String?>(null) })
    val predictionSnapshot by ((currentProvider as? ReplicateMultiEditProvider)?.predictionDiagnostic?.collectAsState()
        ?: remember { mutableStateOf<ReplicatePredictionSnapshot?>(null) })
    val busy = otherBusy
    var message by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var renderJob by remember { mutableStateOf<Job?>(null) }
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var referenceId by remember(id) { mutableStateOf<String?>(null) }
    var referenceBytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var referencePreview by remember(id) { mutableStateOf<Bitmap?>(null) }
    var choosingSource by remember { mutableStateOf<Int?>(null) }
    var enlargedSource by remember { mutableStateOf<Int?>(null) }
    var enlargedResult by remember(id) { mutableStateOf(false) }
    var referenceJob by remember { mutableStateOf<Job?>(null) }
    var resultRenderJob by remember { mutableStateOf<Job?>(null) }
    val allowedSources = remember(id, combineCandidates) { (listOf(id) + combineCandidates).distinct() }
    var combineConsent by remember(currentProvider, chosenSourceId, referenceId) { mutableStateOf(false) }
    DisposableEffect(referenceBytes) { val bytes = referenceBytes; onDispose { bytes?.fill(0) } }
    DisposableEffect(referencePreview) { val bitmap = referencePreview; onDispose { bitmap?.recycle() } }
    LaunchedEffect(referenceId, referenceLoadRevision, editorVisible) {
        referenceJob = currentCoroutineContext()[Job]
        referenceBytes = null; referencePreview = null; combineConsent = false
        val selected = referenceId ?: return@LaunchedEffect
        if (!active.get() || selected !in allowedSources) return@LaunchedEffect
        referenceLoading = true
        var bytes: ByteArray? = null
        var bitmap: Bitmap? = null
        try {
            bytes = loadEditorImage(selected, active, load, loadForEditing)
            if (bytes!!.size > PhotoRenderer.MAX_SOURCE_BYTES) throw AiEditFailure("Second photo is too large.")
            withContext(Dispatchers.Default) { bitmap = PhotoRenderer.render(bytes!!, PhotoEdit(), true) }
            ensureActive()
            if (active.get()) {
                referenceBytes = bytes; bytes = null
                referencePreview = bitmap; bitmap = null
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { message = "Not enough memory to preview the second photo." }
        catch (_: Exception) { message = "Could not open the second photo. Tap Photo 2 to choose again." }
        finally { bytes?.fill(0); bitmap?.recycle(); referenceLoading = false }
    }
    var prompt by remember { mutableStateOf("") }
    var preserveProportions by remember { mutableStateOf(true) }
    var combinePromptExpanded by remember { mutableStateOf(false) }
    var capability by remember(currentProvider) { mutableStateOf(currentProvider?.capabilities?.firstOrNull() ?: AiCapability.GENERATIVE_EDIT) }
    var strokes by remember { mutableStateOf<List<MaskStroke>>(emptyList()) }
    var brush by remember { mutableFloatStateOf(.04f) }
    var aspect by remember { mutableStateOf<Float?>(null) }
    var availabilityRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentProvider) {
        while (true) { availabilityRevision++; kotlinx.coroutines.delay(2000) }
    }
    val providerReady = remember(currentProvider, availabilityRevision) { currentProvider?.ready == true }
    val providerStatus = remember(currentProvider, availabilityRevision) { currentProvider?.availabilityLabel }
    var showConsent by remember { mutableStateOf(false) }
    var rememberConsent by remember { mutableStateOf(false) }
    var sessionConsent by remember(currentProvider) { mutableStateOf(currentProvider?.let { consent.hasConsent(it.id) } ?: false) }
    var discard by remember { mutableStateOf(false) }
    var savingStage by remember { mutableStateOf<String?>(null) }
    fun change(edit: PhotoEdit) { history = history.change(edit); draft = edit; strokes = emptyList() }
    val leave = { if (busy || history.canUndo || aiResult != null) discard = true else { onCancel() } }
    BackHandler(onBack = { if (busy) operation?.cancel() else leave() })
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && (context as? Activity)?.isChangingConfigurations != true) {
            active.set(false); editorVisible = false; choosingSource = null; enlargedSource = null; enlargedResult = false
            operation?.cancel(); loadJob?.cancel(); renderJob?.cancel(); referenceJob?.cancel(); resultRenderJob?.cancel()
            referenceBytes?.fill(0); referencePreview?.recycle(); referenceBytes = null; referencePreview = null; source?.fill(0); cloudResult?.fill(0); preview?.recycle(); source = null; cloudResult = null; preview = null; resultPreview?.recycle(); resultPreview = null; prompt = ""; strokes = emptyList(); onCancel()
        } }
        lifecycle.addObserver(observer)
        onDispose { active.set(false); operation?.cancel(); loadJob?.cancel(); renderJob?.cancel(); referenceJob?.cancel(); resultRenderJob?.cancel(); lifecycle.removeObserver(observer) }
    }
    DisposableEffect(source) { val buffer = source; onDispose { buffer?.fill(0) } }
    DisposableEffect(cloudResult) { val buffer = cloudResult; onDispose { buffer?.fill(0) } }
    DisposableEffect(preview) { val bitmap = preview; onDispose { bitmap?.recycle() } }
    DisposableEffect(resultPreview) { val bitmap = resultPreview; onDispose { bitmap?.recycle() } }
    LaunchedEffect(chosenSourceId, sourceLoadRevision, editorVisible) {
        loadJob = currentCoroutineContext()[Job]
        source = null; preview = null
        if (!active.get()) return@LaunchedEffect
        sourceLoading = true
        var loaded: ByteArray? = null
        try {
            loaded = loadEditorImage(chosenSourceId, active, load, loadForEditing)
            if (loaded!!.size > PhotoRenderer.MAX_SOURCE_BYTES) error("too large")
            ensureActive()
            if (active.get()) { source = loaded; loaded = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { message = "Not enough memory to open this photo." }
        catch (_: Exception) { message = "This image could not be opened for editing." }
        finally { loaded?.fill(0); if (source == null) sourceLoading = false }
    }
    val renderEdit = if (tool == "Crop") PhotoEdit() else draft
    LaunchedEffect(source, renderEdit, editorVisible) {
        renderJob = currentCoroutineContext()[Job]
        if (!active.get()) return@LaunchedEffect
        val bytes = source ?: return@LaunchedEffect
        sourceLoading = true
        var owned: ByteArray? = null
        var rendered: Bitmap? = null
        try {
            owned = bytes.copyOf()
            withContext(Dispatchers.Default) { rendered = PhotoRenderer.render(owned!!, renderEdit, true) }
            ensureActive()
            if (active.get()) { preview = rendered; rendered = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { message = "Not enough memory to preview this image." }
        catch (_: Exception) { message = "Unable to render this image." }
        finally { owned?.fill(0); rendered?.recycle(); if (source === bytes) sourceLoading = false }
    }
    LaunchedEffect(aiResult, editorVisible) {
        resultRenderJob = currentCoroutineContext()[Job]
        resultPreview = null
        if (!active.get()) return@LaunchedEffect
        val bytes = aiResult ?: return@LaunchedEffect
        var owned: ByteArray? = null
        var rendered: Bitmap? = null
        try {
            owned = bytes.copyOf()
            withContext(Dispatchers.Default) { rendered = PhotoRenderer.render(owned!!, PhotoEdit(), true) }
            ensureActive()
            if (active.get()) { resultPreview = rendered; rendered = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { message = "Not enough memory to preview this result." }
        catch (_: Exception) { message = "Unable to render this result." }
        finally { owned?.fill(0); rendered?.recycle() }
    }
    fun chooseSource(number: Int, candidate: String) {
        if (!active.get() || busy || candidate !in allowedSources) return
        combineConsent = false; message = null; enlargedSource = null
        if (number == 1) {
            loadJob?.cancel(); renderJob?.cancel()
            source?.fill(0); source = null; preview = null
            if (candidate != chosenSourceId) { history = EditHistory(PhotoEdit()); draft = history.current; strokes = emptyList() }
            chosenSourceId = candidate; sourceLoadRevision++
        } else if (number == 2) {
            referenceJob?.cancel(); referenceBytes?.fill(0); referenceBytes = null; referencePreview = null
            referenceId = candidate; referenceLoadRevision++
        }
        choosingSource = null
    }
    fun generate() {
        if (!active.get() || busy || source == null || preview == null || currentProvider == null) return
        val resolved = currentProvider.resolve(capability)
        if (resolved == null) { message = "This provider does not support this edit."; return }
        if (capability == AiCapability.COMBINE_PHOTOS && (referenceBytes == null || referencePreview == null)) { message = "Choose a second photo from this vault."; return }
        if (resolved.processing == AiProcessing.CLOUD && (!sessionConsent || (capability == AiCapability.COMBINE_PHOTOS && !combineConsent))) { showConsent = true; return }
        if (!resolved.ready) { message = resolved.availabilityLabel; return }
        val instruction = if (capability == AiCapability.OBJECT_REMOVAL && prompt.isBlank()) "Remove the selected object and preserve the rest of the image." else if (capability == AiCapability.COMBINE_PHOTOS) CombinePrompt.compose(prompt, preserveProportions) else prompt.trim()
        if (instruction.length > 4000) { message = "Your prompt and composition guidance exceed 4,000 characters. Shorten the prompt or disable proportions guidance; your text has not been changed."; return }
        val params = AiParameters(capability, instruction, strokes, aspect, if (capability == AiCapability.COMBINE_PHOTOS) false else enhancePrompt)
        val edit = history.current
        val input = try { source!!.copyOf() } catch (_: OutOfMemoryError) {
            message = "Not enough memory to prepare this photo. Your original is safe."
            return
        }
        val secondInput = try { if (capability == AiCapability.COMBINE_PHOTOS) referenceBytes?.copyOf() else null }
            catch (_: OutOfMemoryError) { input.fill(0); message = "Not enough memory to prepare both photos."; return }
        generationStart = System.nanoTime(); elapsedSeconds = 0
        otherBusy = true; message = "Processing with ${currentProvider.displayName}…"
        operation = scope.launch {
            var encoded: ByteArray? = null
            var result: ByteArray? = null
            try {
                withContext(Dispatchers.Default) {
                    encoded = PhotoRenderer.output(input, edit)
                    result = AiEditPipeline(PhotoRenderer::sanitize).generate(resolved, sessionConsent, encoded!!, params, secondInput)
                }
                ensureActive()
                resultProvenance = AiEditProvenance(resolved.processing, resolved.id, resolved.modelId)
                cloudResult = result; result = null
                val usage = (resolved as? OpenAiImageProvider)?.lastUsage
                message = if (usage != null) "Preview your AI edit before saving. OpenAI usage: ${usage.total} tokens (${usage.input} input, ${usage.output} output)."
                    else "Preview your AI edit before saving."
            } catch (_: TimeoutCancellationException) { message = "AI edit timed out. Try again." }
            catch (cancelled: CancellationException) { message = if (resolved.id == ReplicateSeedreamProvider.ID)
                "Stopped waiting. Replicate cancellation was requested when possible; credit may still be used."
                else "Edit cancelled."; throw cancelled }
            catch (_: OutOfMemoryError) { message = "Not enough memory to process this image." }
            catch (failure: Exception) { message = (failure as? AiEditFailure)?.message ?: "Unable to process this image. Try again." }
            finally { secondInput?.fill(0); input.fill(0); encoded?.fill(0); result?.fill(0); otherBusy = false }
        }
    }
    LaunchedEffect(otherBusy, generationStart) {
        if (otherBusy && generationStart != 0L) while (true) {
            elapsedSeconds = (System.nanoTime() - generationStart) / 1_000_000_000
            delay(1000)
        }
    }
    fun autoCrop() {
        if (busy) return
        val image = preview?.copy(Bitmap.Config.ARGB_8888, false) ?: return
        otherBusy = true
        operation = scope.launch {
            try {
                val crop = withContext(Dispatchers.Default) { VaultAutoCrop.detect(image) }
                ensureActive()
                if (crop == null) message = "No obvious borders detected." else { change(draft.copy(crop = crop)); message = "Auto crop preview — adjust before saving." }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Unable to detect borders. You can crop manually." }
            finally { image.recycle(); otherBusy = false }
        }
    }
    fun save() {
        val selected = aiResult ?: source ?: return
        if (!active.get() || busy) return
        val input = try { selected.copyOf() } catch (_: OutOfMemoryError) {
            message = "Not enough memory to save this image. Your original is unchanged."; return
        }
        val edit = if (aiResult == null) history.current else PhotoEdit()
        val saveRemote = aiResult != null
        // Capture the selected source with the output. Parent metadata/restrictions must follow
        // Photo 1 after replacement, rather than the photo that originally opened the editor.
        val sourceId = chosenSourceId
        val hasAiSave = onSaveAiWithSource != null || onSaveAi != null
        val hasRemoteSave = onSaveRemoteWithSource != null || onSaveRemote != null
        if (saveRemote && !hasAiSave && (!hasRemoteSave || resultProvenance?.processing == AiProcessing.ON_DEVICE)) {
            input.fill(0); message = "AI save unavailable"; return
        }
        val saveAction: (ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit = when {
            saveRemote && hasAiSave -> {
                val provenance = checkNotNull(resultProvenance);
                { bytes, cancelled, completed ->
                    if (onSaveAiWithSource != null) onSaveAiWithSource(sourceId, bytes, provenance, cancelled, completed)
                    else checkNotNull(onSaveAi)(bytes, provenance, cancelled, completed)
                }
            }
            saveRemote -> { bytes, cancelled, completed ->
                if (onSaveRemoteWithSource != null) onSaveRemoteWithSource(sourceId, bytes, cancelled, completed)
                else checkNotNull(onSaveRemote)(bytes, cancelled, completed)
            }
            else -> { bytes, cancelled, completed ->
                if (onSaveWithSource != null) onSaveWithSource(sourceId, bytes, cancelled, completed)
                else onSave(bytes, cancelled, completed)
            }
        }
        otherBusy = true; message = "Saving encrypted copy…"
        if (aiResult != null) savingStage = "Encrypting result…"
        operation = scope.launch {
            var output: ByteArray? = null
            try {
                withContext(Dispatchers.Default) { output = PhotoRenderer.output(input, edit) }
                ensureActive()
                // Import owns the output until its completion callback, including cancellation.
                val bytes = output!!; output = null
                if (savingStage != null) savingStage = "Saving to $vaultLabel…"
                val job = currentCoroutineContext()[Job]!!
                suspendCancellableCoroutine<Unit> { continuation ->
                    try { saveAction(bytes, { !active.get() || !job.isActive }) { result ->
                        bytes.fill(0)
                        if (continuation.isActive) continuation.resumeWith(result)
                    } } catch (failure: Throwable) { bytes.fill(0); if (continuation.isActive) continuation.resumeWith(Result.failure(failure)) }
                }
                message = "Copy saved to $vaultLabel."
                if (savingStage != null) { savingStage = "Saved to $vaultLabel"; delay(600) }
                onCancel()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: OutOfMemoryError) { message = "Not enough memory to save this image." }
            catch (_: Exception) { message = "Copy could not be saved. Your original is unchanged." }
            finally { input.fill(0); output?.fill(0); otherBusy = false; savingStage = null }
        }
    }
    if (!editorVisible) return
    PrivateGalleryTheme(uk.co.traynor.privategallery.core.ui.AppTheme.DARK) {
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().testTag("photo-editor")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = leave) { Text("Cancel") }
            Column(Modifier.weight(1f)) {
                Text("Edit photo", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(vaultLabel, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
            }
            TextButton(onClick = ::save, enabled = source != null && (if (aiResult != null) resultPreview else preview) != null && !busy) { Text("Save copy") }
        }
        onExternalEdit?.let {edit -> TextButton(enabled=!busy,onClick={edit(chosenSourceId)}) {Text("Edit in another app")} }
        if (tool != "AI Edit") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { history = history.undo(); draft = history.current; strokes = emptyList() }, enabled = history.canUndo && !busy && aiResult == null) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = { history = history.redo(); draft = history.current; strokes = emptyList() }, enabled = history.canRedo && !busy && aiResult == null) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            TextButton(onClick = { history = history.reset(); draft = history.current; strokes = emptyList() }, enabled = !busy && aiResult == null) { Text("Reset") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight * 1.35f
            val combineSetup = tool == "AI Edit" && capability == AiCapability.COMBINE_PHOTOS && aiResult == null
            val panelHeight = if (combineSetup || landscape) maxHeight else if (tool == "AI Edit") minOf(480.dp, maxHeight * .68f)
                else minOf(260.dp, maxHeight * .55f)
            val imageCanvas: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag("editor-canvas"), contentAlignment = Alignment.Center) {
            (if (aiResult != null) resultPreview else preview)?.let { bitmap ->
                if (tool == "Crop" && aiResult == null && !busy) CropCanvas(bitmap, draft.crop, { crop -> draft = draft.copy(crop = crop) }, onGestureFinished = { change(draft) })
                else if (tool == "AI Edit" && aiResult == null && capability in setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL) && !busy) MaskCanvas(bitmap, strokes, brush, { if (strokes.size < 128 && strokes.sumOf { stroke -> stroke.points.size } + it.points.size <= 16384) strokes = strokes + it else message = "Selection limit reached. Clear or undo a stroke." })
                else if (tool == "AI Edit" && aiResult == null && capability == AiCapability.OUTPAINT) ExpandCanvasPreview(bitmap, aspect)
                else Image(bitmap.asImageBitmap(), if (aiResult != null) "AI result preview" else "Photo preview",
                    Modifier.fillMaxSize().testTag(if (aiResult != null) "ai-result-preview" else "editor-source-preview")
                        .then(if (aiResult != null) Modifier.clickable(enabled = !busy, onClickLabel = "Open result preview") { enlargedResult = true } else Modifier),
                    contentScale = ContentScale.Fit)
            } ?: if (message == null) CircularProgressIndicator() else Text("Preview unavailable", color = Color.White)
        }
            }
            val toolPanel: @Composable () -> Unit = {
        Surface(modifier = if (combineSetup) Modifier.fillMaxSize() else Modifier,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().heightIn(max = panelHeight)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Crop" to Icons.Default.Crop, "Adjust" to Icons.Default.Tune, "AI Edit" to Icons.Default.AutoAwesome).forEach { (name, icon) ->
                        FilterChip(tool == name, { tool = name; draft = history.current; strokes = emptyList() }, label = { Text(name) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, enabled = !busy && aiResult == null)
                    }
                }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    message?.let { Text(it, Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall) }
                    if (otherBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (aiResult != null) {
                        Text("Review your result", style = MaterialTheme.typography.titleMedium)
                        Text("Save an encrypted copy to $vaultLabel when you’re happy with the edit.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (capability == AiCapability.COMBINE_PHOTOS) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Preserve realistic proportions", Modifier.weight(1f))
                            Switch(preserveProportions, { preserveProportions = it }, enabled = !busy,
                                modifier = Modifier.semantics { contentDescription = "Preserve realistic proportions" })
                        }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { enlargedResult = false; cloudResult = null; resultPreview = null; resultProvenance = null; message = null }, enabled = !busy) { Text("Discard") }
                            FilledTonalButton(onClick = { enlargedResult = false; cloudResult = null; resultPreview = null; resultProvenance = null; generate() }, enabled = !busy) { Text("Regenerate") }
                        }
                    } else when (tool) {
                        "Crop" -> {
                            Text("Crop the original; rotate and flip apply afterwards.", style = MaterialTheme.typography.bodySmall)
                            Text("Very large copies may use a reduced resolution to fit device memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row { TextButton(onClick = ::autoCrop, enabled = !busy && preview != null) { Text("Auto crop") }; TextButton(onClick = { change(draft.rotate()); tool = "Adjust" }, enabled = !busy) { Icon(Icons.Default.RotateRight, null); Text("Rotate") }; TextButton(onClick = { change(draft.copy(flipHorizontal = !draft.flipHorizontal)); tool = "Adjust" }, enabled = !busy) { Icon(Icons.Default.Flip, null); Text("Flip") } }
                        }
                        "Adjust" -> {
                            AdjustmentSlider("Brightness", draft.brightness, -1f..1f, !busy, { draft = draft.copy(brightness = it) }, { change(draft) })
                            AdjustmentSlider("Contrast", draft.contrast, 0f..2f, !busy, { draft = draft.copy(contrast = it) }, { change(draft) })
                            AdjustmentSlider("Saturation", draft.saturation, 0f..2f, !busy, { draft = draft.copy(saturation = it) }, { change(draft) })
                        }
                        "AI Edit" -> {
                            if (providerConfigured) {
                                Text(if (capability == AiCapability.COMBINE_PHOTOS) "Source photos" else "Source photo", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AiSourceCard(1, preview, true, sourceLoading, !busy, { choosingSource = 1 }, { enlargedSource = 1 }, Modifier.weight(1f))
                                    if (capability == AiCapability.COMBINE_PHOTOS)
                                        AiSourceCard(2, referencePreview, referenceId != null, referenceLoading, !busy, { choosingSource = 2 }, { enlargedSource = 2 }, Modifier.weight(1f))
                                }
                                if (capability == AiCapability.COMBINE_PHOTOS) {
                                    Text("Choose two photos from $vaultLabel. Both displayed photos are sent to the provider.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Your instructions stay authoritative. Optional composition guidance can be reviewed below.", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            if (!providerConfigured) Text("AI editing · Not configured", style = MaterialTheme.typography.titleSmall)
                            val providerChoices = AiProviderChoice.entries.filter { choice ->
                                AiProviderRegistry.provider(choice)?.configured == true || choice == selectedChoice
                            }
                            AiSinglePicker("Provider", AiPickerItem(selectedChoice.name, selectedChoice.name.lowercase().replaceFirstChar { it.uppercase() },
                                if (providerConfigured) "Connected" else "Configure in Settings", ""),
                                providerChoices.map { choice -> AiPickerItem(choice.name, choice.name.lowercase().replaceFirstChar { it.uppercase() },
                                    if (AiProviderRegistry.provider(choice)?.configured == true) "Connected" else "Configure in Settings", "") },
                                onSelect = { key ->
                                    val choice = AiProviderChoice.entries.first { it.name == key }
                                    selectedProvider = AiProviderRegistry.provider(choice)
                                    selectedChoice = choice
                                    AiProviderRegistry.select(choice)
                                    strokes = emptyList()
                                }, enabled = !busy)
                            if (!providerConfigured) Text("Configure your selected provider in AI editing settings.", style = MaterialTheme.typography.bodySmall)
                            else if (currentProvider != null) {
                                if (selectedChoice == AiProviderChoice.REPLICATE && currentProvider is ReplicateMultiEditProvider) {
                                    val eligible = if (capability == AiCapability.GENERATIVE_EDIT)
                                        ReplicateModelCapabilities.editModels else ReplicateModelCapabilities.editModelsFor(capability)
                                    // Selecting an operation can change the eligible model; the selected card
                                    // always reflects an operation the model can actually perform.
                                    AiSinglePicker("Model", replicateModel.pickerItem(consent.relaxSeedreamModeration(), proResolution),
                                        eligible.map { it.pickerItem(consent.relaxSeedreamModeration(), proResolution) },
                                        onSelect = { key ->
                                            val model = ReplicateEditModel.entries.first { it.name == key }
                                            replicateModel = model; replicateModels.select(model)
                                            capability = if (capability in model.tools) capability else model.tools.first()
                                            strokes = emptyList()
                                        }, enabled = !busy)
                                    if (replicateModel == ReplicateEditModel.SEEDREAM_5_PRO) {
                                        Text("Quality", style = MaterialTheme.typography.titleSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("1K", "2K").forEach { size -> FilterChip(proResolution == size, {
                                                proResolution = size; replicateModels.setProResolution(size)
                                            }, label = { Text(size) }, modifier = Modifier.testTag("edit-quality-$size"), enabled = !busy) }
                                        }
                                        Text("Estimated provider cost · ${if (proResolution == "1K") "≈$0.045" else "≈$0.09"} / image", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(if (replicateModel == ReplicateEditModel.FILL) "Select an area to replace. Areas outside the selection are preserved." else "Whole-image edit. Selection masks and strength are unavailable for this model.", style = MaterialTheme.typography.bodySmall)
                                    Text("Estimated model price; uses Replicate API credit · one output. Your final charge may vary.", style = MaterialTheme.typography.bodySmall)
                                    if (predictionSnapshot != null)
                                        TextButton(onClick = { predictionDiagnostic = true }) { Text("Show prediction details") }
                                }
                                val availableTools = if (selectedChoice == AiProviderChoice.REPLICATE && currentProvider is ReplicateMultiEditProvider)
                                    ReplicateModelCapabilities.editModels.flatMap { it.tools }.distinct() else currentProvider.capabilities.toList()
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { availableTools.forEach { cap -> FilterChip(capability == cap, {
                                    if (selectedChoice == AiProviderChoice.REPLICATE && cap !in replicateModel.tools) {
                                        replicateModel = ReplicateModelCapabilities.editModelsFor(cap).first()
                                        replicateModels.select(replicateModel)
                                    }
                                    capability = cap; strokes = emptyList()
                                }, label = { Text(cap.label) }, enabled = !busy) } }
                                Spacer(Modifier.height(12.dp))
                                Text("Instructions", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(prompt, { if (it.length <= 4000) prompt = it }, label = { Text("Describe your change") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("ai-edit-prompt"), enabled = !busy, minLines = 2, maxLines = 5)
                                if (capability == AiCapability.COMBINE_PHOTOS) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Preserve realistic proportions", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(preserveProportions, { preserveProportions = it }, enabled = !busy,
                                            modifier = Modifier.semantics { contentDescription = "Preserve realistic proportions" })
                                    }
                                    Text("Guidance yields to your requested composition, including unusual scale. Results can still vary.", style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { combinePromptExpanded = !combinePromptExpanded }) {
                                        Text(if (combinePromptExpanded) "Hide effective prompt" else "Show effective prompt")
                                    }
                                    if (combinePromptExpanded) Text(CombinePrompt.compose(prompt, preserveProportions),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("combine-effective-prompt"))
                                }
                                if (selectedChoice == AiProviderChoice.REPLICATE && capability != AiCapability.COMBINE_PHOTOS) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Prompt enhancement", Modifier.weight(1f))
                                        Switch(enhancePrompt, { enhancePrompt = it }, enabled = !busy)
                                    }
                                    TextButton(onClick = { promptDiagnostic = true }) { Text("Show original and effective prompt") }
                                }
                                if (capability in setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL)) {
                                    AdjustmentSlider("Brush size", brush, .005f.. .2f, !busy, { brush = it }, {})
                                    Row { TextButton(onClick = { strokes = strokes.dropLast(1) }, enabled = !busy && strokes.isNotEmpty()) { Text("Undo stroke") }; TextButton(onClick = { strokes = emptyList() }, enabled = !busy && strokes.isNotEmpty()) { Text("Clear selection") } }
                                }
                                if (capability == AiCapability.OUTPAINT) AspectChoices(aspect, { aspect = it }, !busy)
                                if (!providerReady) Text(providerStatus ?: "Unavailable", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp), enabled = !busy && providerReady && source != null && preview != null && (capability != AiCapability.COMBINE_PHOTOS || (referenceBytes != null && referencePreview != null)) && (capability == AiCapability.OBJECT_REMOVAL || prompt.isNotBlank() || capability == AiCapability.BACKGROUND_REMOVE)) { Text("Generate") }
                            }
                        }
                    }

                }
            }
        }
            }
            if (combineSetup) toolPanel()
            else if (landscape) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { imageCanvas() }
                Box(Modifier.widthIn(max = 320.dp).fillMaxHeight().weight(.7f)) { toolPanel() }
            } else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { imageCanvas() }
                toolPanel()
            }
        }
    }
    choosingSource?.let { number ->
        AiSourcePicker(number, vaultLabel,
            allowedSources.filter { it != (if (number == 1) referenceId else chosenSourceId) },
            active, load, loadForEditing, { chooseSource(number, it) }, { choosingSource = null })
    }
    enlargedSource?.let { number ->
        val bitmap = if (number == 1) preview else referencePreview
        if (bitmap != null) androidx.compose.ui.window.Dialog(onDismissRequest = { enlargedSource = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Photo $number", style = MaterialTheme.typography.titleMedium)
                    Image(bitmap.asImageBitmap(), "Photo $number enlarged preview", Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp).testTag("ai-large-preview"), contentScale = ContentScale.Fit)
                    TextButton(onClick = { enlargedSource = null }, modifier = Modifier.align(Alignment.End)) { Text("Close preview") }
                }
            }
        }
    }
    if (enlargedResult && aiResult != null) resultPreview?.let { bitmap ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { enlargedResult = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Result preview", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { enlargedResult = false }) { Text("Close preview") }
                    }
                    Image(bitmap.asImageBitmap(), "Full-screen AI result preview",
                        Modifier.fillMaxWidth().weight(1f).testTag("ai-result-large-preview"), contentScale = ContentScale.Fit)
                }
            }
        }
    }
    if (promptDiagnostic) PromptDiagnosticDialog(prompt,
        runCatching {promptTemplates.effective(prompt,if(replicateModel==ReplicateEditModel.FILL)PromptKind.MASK else PromptKind.EDIT,
            replicateModel.modelId,enhancePrompt)}.getOrElse {it.message ?: "Invalid template"},onClose={promptDiagnostic=false})
    if (predictionDiagnostic) PredictionDiagnosticDialog(predictionSnapshot?.let {snapshot ->
        "Provider: Replicate\nModel: ${snapshot.model}\nQuality: ${snapshot.resolution}\nPrediction: ${snapshot.predictionId ?: "Not confirmed"}\nState: ${snapshot.state}\nObserved: ${snapshot.observedStates.joinToString()}\nStatus checks: ${snapshot.pollingAttempts}\nElapsed: ${snapshot.elapsedMillis / 1000}s"
    } ?: "No prediction in this session.",onClose={predictionDiagnostic=false})
    if (otherBusy && tool == "AI Edit" && savingStage == null) AiOperationProgressDialog(
        provider=if(currentProvider is ReplicateMultiEditProvider) "${replicateModel.label}${if(replicateModel==ReplicateEditModel.SEEDREAM_5_PRO) " · $proResolution" else ""}" else currentProvider?.displayName ?: "AI editing",
        status=providerProgress ?: "Generating…",elapsed="${elapsedSeconds / 60}m ${elapsedSeconds % 60}s elapsed",
        location=if(currentProvider?.processing==AiProcessing.ON_DEVICE)"Processed on this device" else "Processed remotely with ${currentProvider?.displayName ?: "the provider"}",
        onCancel={operation?.cancel()})
    savingStage?.let {stage ->AiSaveProgressDialog(stage,stage=="Saved to $vaultLabel",onCancel={operation?.cancel()})}
    if (showConsent) AlertDialog(scrollBody = false, onDismissRequest = { showConsent = false }, title = { Text("Remote AI processing") }, text = { Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
        Text("${if (capability == AiCapability.COMBINE_PHOTOS) "Combine Photos uploads both displayed photos and your custom prompt with any enabled, previewable composition guidance" else "AI editing sends the selected image and your edit instructions"} to ${currentProvider?.displayName ?: "the configured AI provider"} for processing.")
        Text(AiProviderRegistry.NETWORK_POLICY, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberConsent, { rememberConsent = it }); Text("Remember for this provider") }
    } }, confirmButton = { TextButton(onClick = { showConsent = false; sessionConsent = true; combineConsent = capability == AiCapability.COMBINE_PHOTOS; if (rememberConsent) currentProvider?.let { consent.remember(it.id) }; generate() }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard edits?") }, text = { Text("Your original stays untouched.") }, confirmButton = { TextButton(onClick = { active.set(false); operation?.cancel(); onCancel() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, changed: (Float) -> Unit, finished: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.width(86.dp), style = MaterialTheme.typography.labelMedium); Slider(value, changed, Modifier.weight(1f), enabled = enabled, valueRange = range, onValueChangeFinished = finished) }
}
