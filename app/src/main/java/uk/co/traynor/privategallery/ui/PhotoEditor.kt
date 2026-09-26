package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

/** Buffers never enter saved-instance state. Backgrounding cancels and discards edits. */
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
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val consent = remember { AiConsentStore(context) }
    val replicateModels = remember { ReplicateEditModelStore(context) }
    var replicateModel by remember { mutableStateOf(replicateModels.selected()) }
    var selectedProvider by remember(provider) { mutableStateOf(provider) }
    var selectedChoice by remember(provider) { mutableStateOf(AiProviderRegistry.choice) }
    val currentProvider = selectedProvider
    val providerConfigured = currentProvider?.configured == true
    var history by remember(id) { mutableStateOf(EditHistory(PhotoEdit(crop = initialCrop ?: NormalizedCrop.ORIGINAL))) }
    var draft by remember(id) { mutableStateOf(history.current) }
    var tool by remember { mutableStateOf("Crop") }
    var source by remember(id) { mutableStateOf<ByteArray?>(null) }
    var preview by remember(id) { mutableStateOf<Bitmap?>(null) }
    var resultProvenance by remember(id) { mutableStateOf<AiEditProvenance?>(null) }
    var cloudResult by remember(id) { mutableStateOf<ByteArray?>(null) }
    val aiResult = cloudResult
    var otherBusy by remember { mutableStateOf(false) }
    val busy = otherBusy
    var message by remember { mutableStateOf<String?>(null) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var renderJob by remember { mutableStateOf<Job?>(null) }
    val active = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var prompt by remember { mutableStateOf("") }
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
            active.set(false); operation?.cancel(); loadJob?.cancel(); renderJob?.cancel()
            source?.fill(0); cloudResult?.fill(0); preview?.recycle(); source = null; cloudResult = null; preview = null; onCancel()
        } }
        lifecycle.addObserver(observer)
        onDispose { active.set(false); operation?.cancel(); lifecycle.removeObserver(observer) }
    }
    DisposableEffect(source) { val buffer = source; onDispose { buffer?.fill(0) } }
    DisposableEffect(cloudResult) { val buffer = cloudResult; onDispose { buffer?.fill(0) } }
    DisposableEffect(preview) { val bitmap = preview; onDispose { bitmap?.recycle() } }
    LaunchedEffect(id) {
        loadJob = currentCoroutineContext()[Job]
        try {
            val loaded = suspendCancellableCoroutine<ByteArray> { continuation ->
                val callback: (Result<ByteArray>) -> Unit = { result ->
                    val bytes = result.getOrNull()
                    if (!continuation.isActive || !active.get()) bytes?.fill(0)
                    else result.fold({ continuation.resume(it) { bytes?.fill(0) } }, { continuation.resumeWith(Result.failure(it)) })
                }
                if (loadForEditing != null) loadForEditing(id, { !continuation.isActive || !active.get() }, callback)
                else if (load != null) load(id, callback)
                else continuation.resumeWith(Result.failure(IllegalStateException()))
            }
            if (loaded.size > PhotoRenderer.MAX_SOURCE_BYTES) { loaded.fill(0); error("too large") }
            source = loaded
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message = "This image could not be opened for editing." }
    }
    val renderEdit = if (aiResult != null || tool == "Crop") PhotoEdit() else draft
    LaunchedEffect(source, renderEdit, aiResult) {
        renderJob = currentCoroutineContext()[Job]
        val owned = (aiResult ?: source ?: return@LaunchedEffect).copyOf()
        var rendered: Bitmap? = null
        try {
            withContext(Dispatchers.Default) { rendered = PhotoRenderer.render(owned, renderEdit, true) }
            ensureActive()
            preview = rendered; rendered = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { message = "Not enough memory to preview this image." }
        catch (_: Exception) { message = "Unable to render this image." }
        finally { owned.fill(0); rendered?.recycle() }
    }
    fun generate() {
        if (busy || source == null || currentProvider == null) return
        val resolved = currentProvider.resolve(capability)
        if (resolved == null) { message = "This provider does not support this edit."; return }
        if (resolved.processing == AiProcessing.CLOUD && !sessionConsent) { showConsent = true; return }
        if (!resolved.ready) { message = resolved.availabilityLabel; return }
        val instruction = if (capability == AiCapability.OBJECT_REMOVAL && prompt.isBlank()) "Remove the selected object and preserve the rest of the image." else prompt.trim()
        val params = AiParameters(capability, instruction, strokes, aspect)
        val edit = history.current
        val input = try { source!!.copyOf() } catch (_: OutOfMemoryError) {
            message = "Not enough memory to prepare this photo. Your original is safe."
            return
        }
        otherBusy = true; message = "Processing with ${currentProvider.displayName}…"
        operation = scope.launch {
            var encoded: ByteArray? = null
            var result: ByteArray? = null
            try {
                withContext(Dispatchers.Default) {
                    encoded = PhotoRenderer.output(input, edit)
                    result = AiEditPipeline(PhotoRenderer::sanitize).generate(resolved, sessionConsent, encoded!!, params)
                }
                ensureActive()
                resultProvenance = AiEditProvenance(resolved.processing, resolved.id, resolved.modelId)
                cloudResult = result; result = null
                val usage = (resolved as? OpenAiImageProvider)?.lastUsage
                message = if (usage != null) "Preview your AI edit before saving. OpenAI usage: ${usage.total} tokens (${usage.input} input, ${usage.output} output)."
                    else "Preview your AI edit before saving."
            } catch (_: TimeoutCancellationException) { message = "AI edit timed out. Try again." }
            catch (cancelled: CancellationException) { message = "Edit cancelled."; throw cancelled }
            catch (_: OutOfMemoryError) { message = "Not enough memory to process this image." }
            catch (failure: Exception) { message = (failure as? AiEditFailure)?.message ?: "Unable to process this image. Try again." }
            finally { input.fill(0); encoded?.fill(0); result?.fill(0); otherBusy = false }
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
        if (busy) return
        val input = selected.copyOf()
        val edit = if (aiResult == null) history.current else PhotoEdit()
        val saveRemote = aiResult != null
        if (saveRemote && onSaveAi == null && (onSaveRemote == null || resultProvenance?.processing == AiProcessing.ON_DEVICE)) { input.fill(0); message = "AI save unavailable"; return }
        val saveAction: (ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit = if (saveRemote && onSaveAi != null) {
            val provenance = checkNotNull(resultProvenance);
            { bytes, cancelled, completed -> onSaveAi(bytes, provenance, cancelled, completed) }
        } else if (saveRemote) checkNotNull(onSaveRemote) else onSave
        otherBusy = true; message = "Saving encrypted copy…"
        if (aiResult != null) savingStage = "Encrypting result…"
        operation = scope.launch {
            var output: ByteArray? = null
            try {
                withContext(Dispatchers.Default) { output = PhotoRenderer.output(input, edit) }
                ensureActive()
                // Import owns the output until its completion callback, including cancellation.
                val bytes = output!!; output = null
                if (savingStage != null) savingStage = "Saving to Vault…"
                val job = currentCoroutineContext()[Job]!!
                suspendCancellableCoroutine<Unit> { continuation ->
                    try { saveAction(bytes, { !active.get() || !job.isActive }) { result ->
                        bytes.fill(0)
                        if (continuation.isActive) continuation.resumeWith(result)
                    } } catch (failure: Throwable) { bytes.fill(0); if (continuation.isActive) continuation.resumeWith(Result.failure(failure)) }
                }
                message = "Copy saved to Vault."
                if (savingStage != null) { savingStage = "Saved to Vault"; delay(600) }
                onCancel()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: OutOfMemoryError) { message = "Not enough memory to save this image." }
            catch (_: Exception) { message = "Copy could not be saved. Your original is unchanged." }
            finally { input.fill(0); output?.fill(0); otherBusy = false; savingStage = null }
        }
    }
    PrivateGalleryTheme(uk.co.traynor.privategallery.core.ui.AppTheme.DARK) {
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).testTag("photo-editor")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = leave) { Text("Cancel") }
            Text("Edit photo", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
            TextButton(onClick = ::save, enabled = source != null && preview != null && !busy) { Text("Save copy") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = { history = history.undo(); draft = history.current; strokes = emptyList() }, enabled = history.canUndo && !busy && aiResult == null) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton(onClick = { history = history.redo(); draft = history.current; strokes = emptyList() }, enabled = history.canRedo && !busy && aiResult == null) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            TextButton(onClick = { history = history.reset(); draft = history.current; strokes = emptyList() }, enabled = !busy && aiResult == null) { Text("Reset") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight * 1.35f
            val panelHeight = if (landscape) maxHeight else minOf(260.dp, maxHeight * .55f)
            val imageCanvas: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag("editor-canvas"), contentAlignment = Alignment.Center) {
            preview?.let { bitmap ->
                if (tool == "Crop" && aiResult == null && !busy) CropCanvas(bitmap, draft.crop, { crop -> draft = draft.copy(crop = crop) }, onGestureFinished = { change(draft) })
                else if (tool == "AI Edit" && aiResult == null && capability in setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL) && !busy) MaskCanvas(bitmap, strokes, brush, { if (strokes.size < 128 && strokes.sumOf { stroke -> stroke.points.size } + it.points.size <= 16384) strokes = strokes + it else message = "Selection limit reached. Clear or undo a stroke." })
                else if (tool == "AI Edit" && aiResult == null && capability == AiCapability.OUTPAINT) ExpandCanvasPreview(bitmap, aspect)
                else Image(bitmap.asImageBitmap(), "Photo preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } ?: if (message == null) CircularProgressIndicator() else Text("Preview unavailable", color = Color.White)
        }
            }
            val toolPanel: @Composable () -> Unit = {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().heightIn(max = panelHeight).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
                message?.let { Text(it, Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall) }
                if (otherBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = { operation?.cancel() }) { Text("Cancel processing") } }
                if (aiResult != null) {
                    Row { TextButton(onClick = { cloudResult = null; message = null }, enabled = !busy) { Text("Cancel result") }; TextButton(onClick = { cloudResult = null; generate() }, enabled = !busy) { Text("Try again") } }
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
                        if (!providerConfigured) Text("AI editing · Not configured", style = MaterialTheme.typography.titleSmall)
                        Text("Provider · ${currentProvider?.displayName ?: "Not configured"}", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiProviderChoice.entries.forEach { choice ->
                                FilterChip(selectedChoice == choice, onClick = {
                                    selectedProvider = AiProviderRegistry.provider(choice)
                                    selectedChoice = choice
                                    AiProviderRegistry.select(choice)
                                    strokes = emptyList()
                                }, label = { Text(choice.label) }, enabled = !busy)
                            }
                        }
                        Text(if (currentProvider?.processing == AiProcessing.ON_DEVICE) "On-device processing" else "Cloud · Remote processing", style = MaterialTheme.typography.bodySmall)
                        if (!providerConfigured) Text("Configure your selected provider in AI editing settings.", style = MaterialTheme.typography.bodySmall)
                        else if (currentProvider != null) {
                            if (selectedChoice == AiProviderChoice.REPLICATE && currentProvider is ReplicateMultiEditProvider) {
                                Text("Model", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ReplicateModelCapabilities.editModels.forEach { model ->
                                        FilterChip(replicateModel == model, onClick = {
                                            replicateModel = model
                                            replicateModels.select(model)
                                            capability = model.tools.first()
                                            strokes = emptyList()
                                        }, label = { Text("${model.label}${if (model == ReplicateEditModel.SEEDREAM && consent.relaxSeedreamModeration()) " (Adult)" else ""} · ${model.priceLabel}") }, enabled = !busy)
                                    }
                                }
                                Text(replicateModel.description, style = MaterialTheme.typography.bodySmall)
                                Text(if (replicateModel == ReplicateEditModel.FILL) "Select an area to replace. Areas outside the selection are preserved." else "Whole-image edit. Selection masks and strength are unavailable for this model.", style = MaterialTheme.typography.bodySmall)
                                Text("Estimated model price; uses Replicate API credit · one output. Your final charge may vary.", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { currentProvider.capabilities.forEach { cap -> FilterChip(capability == cap, { capability = cap; strokes = emptyList() }, label = { Text(cap.label) }, enabled = !busy) } }
                            OutlinedTextField(prompt, { if (it.length <= 4000) prompt = it }, label = { Text("Describe your change") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, maxLines = 3)
                            if (capability in setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL)) {
                                AdjustmentSlider("Brush size", brush, .005f.. .2f, !busy, { brush = it }, {})
                                Row { TextButton(onClick = { strokes = strokes.dropLast(1) }, enabled = !busy && strokes.isNotEmpty()) { Text("Undo stroke") }; TextButton(onClick = { strokes = emptyList() }, enabled = !busy && strokes.isNotEmpty()) { Text("Clear selection") } }
                            }
                            if (capability == AiCapability.OUTPAINT) AspectChoices(aspect, { aspect = it }, !busy)
                            if (!providerReady) Text(providerStatus ?: "Unavailable", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { generate() }, enabled = !busy && providerReady && source != null && (capability == AiCapability.OBJECT_REMOVAL || prompt.isNotBlank() || capability == AiCapability.BACKGROUND_REMOVE)) { Text("Generate") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Crop" to Icons.Default.Crop, "Adjust" to Icons.Default.Tune, "AI Edit" to Icons.Default.AutoAwesome).forEach { (name, icon) ->
                        FilterChip(tool == name, { tool = name; draft = history.current; strokes = emptyList() }, label = { Text(name) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, enabled = !busy && aiResult == null)
                    }
                }
            }
        }            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { imageCanvas() }
                Box(Modifier.widthIn(max = 320.dp).fillMaxHeight().weight(.7f)) { toolPanel() }
            } else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { imageCanvas() }
                toolPanel()
            }
        }
    }

    savingStage?.let { stage -> AlertDialog(onDismissRequest = {}, title = { Text(stage) },
        text = { if (stage != "Saved to Vault") LinearProgressIndicator(Modifier.fillMaxWidth()) else Text("Your encrypted copy is saved.") },
        confirmButton = { if (stage != "Saved to Vault") TextButton(onClick = { operation?.cancel() }) { Text("Cancel") } },
    ) }
    if (showConsent) AlertDialog(onDismissRequest = { showConsent = false }, title = { Text("Remote AI processing") }, text = { Column {
        Text("AI editing sends the selected image and your edit instructions to ${currentProvider?.displayName ?: "the configured AI provider"} for processing.")
        Text(AiProviderRegistry.NETWORK_POLICY, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberConsent, { rememberConsent = it }); Text("Remember for this provider") }
    } }, confirmButton = { TextButton(onClick = { showConsent = false; sessionConsent = true; if (rememberConsent) currentProvider?.let { consent.remember(it.id) }; generate() }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard edits?") }, text = { Text("Your original stays untouched.") }, confirmButton = { TextButton(onClick = { active.set(false); operation?.cancel(); onCancel() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
    }
}

@Composable
private fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, changed: (Float) -> Unit, finished: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.width(86.dp), style = MaterialTheme.typography.labelMedium); Slider(value, changed, Modifier.weight(1f), enabled = enabled, valueRange = range, onValueChangeFinished = finished) }
}
