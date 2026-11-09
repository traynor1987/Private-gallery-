package uk.co.traynor.privategallery.home2

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.home2.SpaceContext
import kotlin.math.roundToInt

private enum class ArcadeTitle(val id: String, val label: String, val genre: String, val accent: Color) {
    TILES("2048", "2048", "Slide and merge", Color(0xFFFFCB83)),
    SUDOKU("sudoku", "Sudoku", "Logic puzzle", Color(0xFFC3B7FF)),
    SNAKE("snake", "Snake", "Classic survival", Color(0xFF9AE9DD))
}

internal val ArcadeLevels = listOf("Easy", "Medium", "Hard", "Expert")
private val ArcadeShape = RoundedCornerShape(24.dp)

@Composable
internal fun ArcadeScreen(home: PreviewData?, initialGame: String? = null, active: Boolean = true, onHome: () -> Unit) {
    val context = LocalContext.current as? SpaceContext
    val launcherSession = home == null
    val model = remember(context, launcherSession) {
        runCatching { if (launcherSession) ArcadeData.launcherSession() else ArcadeData(checkNotNull(context)) }
    }.getOrNull()
    if (model == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            PreviewHeader("Nova Arcade", "Saved data could not be read", onHome)
            Text("Your existing encrypted saves have been preserved. Lock and reopen this Space, or restore a verified backup.")
        }
        return
    }

    val initialTitle = ArcadeTitle.entries.firstOrNull { it.id == initialGame }
    var title by remember { mutableStateOf(initialTitle) }
    var section by remember { mutableStateOf<String?>(null) }
    var newGame by remember { mutableStateOf<ArcadeTitle?>(null) }
    var help by remember { mutableStateOf<ArcadeTitle?>(null) }
    var level by remember { mutableIntStateOf(model.sudoku?.level ?: 0) }
    val lifecycle = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val view = LocalView.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 25) }.getOrNull() }
    fun feedback() {
        if (active && resumed && model.current()) {
            if (model.sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
            if (model.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
    fun open(game: ArcadeTitle, fresh: Boolean = false) {
        model.pause()
        if (fresh || !model.hasSave(game)) model.start(game.id, level)
        model.visit(game.id)
        title = game
        section = null
    }
    fun openSettings() {
        model.pause()
        section = "settings"
    }
    fun back() {
        model.pause()
        when {
            section != null -> section = null
            title != null -> title = null
            else -> onHome()
        }
    }
    LaunchedEffect(initialGame) {
        if (initialTitle == null) {
            model.pause()
            title = null
            section = null
        } else open(initialTitle)
    }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    resumed = false
                    model.pause()
                }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            model.pause()
        }
    }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(active) { if (!active) model.pause() }
    LaunchedEffect(title, section, active, resumed, model.speed) {
        val playing = title ?: return@LaunchedEffect
        if (section != null || playing == ArcadeTitle.TILES) return@LaunchedEffect
        while (active && resumed && model.current()) {
            delay(if (playing == ArcadeTitle.SNAKE) 440L - model.speed * 60L else 1000L)
            if (!active || !resumed || !model.current()) break
            if (playing == ArcadeTitle.SNAKE) {
                model.snake?.takeIf { !it.paused && !it.over }?.let { game ->
                    val next = game.tick()
                    model.save(next)
                    if (next.score > game.score || next.over) feedback()
                }
            } else {
                model.sudoku?.takeIf { !it.paused && !it.complete() }?.let { model.save(it.tick()) }
            }
        }
    }

    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxSize().testTag("nova-arcade"),
        color = colors.background,
    ) {
        Column(Modifier.safeDrawingPadding()) {
            val heading = when (section) {
                "settings" -> "Arcade settings"
                "achievements" -> "Achievements"
                else -> title?.label ?: "Nova Arcade"
            }
            PreviewHeader(heading, if (launcherSession) "Offline · this session only" else "Offline · saved in this Space", ::back) {
                when {
                    section != null -> Unit
                    title == null -> {
                        IconButton(onClick = { section = "achievements" }, modifier = Modifier.testTag("arcade-achievements")) {
                            Icon(Icons.Default.EmojiEvents, "Achievements", tint = colors.secondary)
                        }
                        IconButton(onClick = ::openSettings, modifier = Modifier.testTag("arcade-settings")) {
                            Icon(Icons.Default.Settings, "Arcade settings")
                        }
                    }
                    else -> {
                        if (title == ArcadeTitle.SNAKE) {
                            IconButton(onClick = ::openSettings) { Icon(Icons.Default.Settings, "Snake settings") }
                        }
                        IconButton(onClick = {
                            model.pause()
                            help = title
                        }) { Icon(Icons.Default.HelpOutline, "Game help") }
                    }
                }
            }
            model.error?.let {
                Surface(color = colors.errorContainer, shape = ArcadeShape, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(it, Modifier.padding(12.dp), color = colors.onErrorContainer)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (section) {
                    "achievements" -> ArcadeAchievements(model)
                    "settings" -> ArcadeSettings(model)
                    else -> when (val game = title) {
                        null -> ArcadeCatalogue(home, model, onOpen = { open(it) }, onNew = { newGame = it })
                        ArcadeTitle.TILES -> model.tiles?.let { state ->
                            TilesGameplay(state, model.bestTiles, onMove = { direction ->
                                if (active && resumed) {
                                    model.tiles?.let { current ->
                                        val next = current.move(direction)
                                        if (next !== current) {
                                            model.save(next)
                                            feedback()
                                        }
                                    }
                                }
                            }, onUndo = { model.save(state.undo()) }, onRestart = { newGame = game })
                        }
                        ArcadeTitle.SUDOKU -> model.sudoku?.let { state ->
                            SudokuGameplay(state, onSave = {
                                model.save(it)
                                feedback()
                            }, onRestart = { newGame = game })
                        }
                        ArcadeTitle.SNAKE -> model.snake?.let { state ->
                            val turn: (Int) -> Unit = { direction ->
                                if (active && resumed) model.snake?.let { model.save(it.turn(direction)) }
                            }
                            SnakeGameplay(state, model.bestSnake, onTurn = turn, onArrowTurn = {
                                turn(it)
                                feedback()
                            }, onPause = {
                                model.snake?.let { model.save(it.pause(!it.paused)) }
                            }, onRestart = { newGame = game })
                        }
                    }
                }
            }
        }
    }

    newGame?.let { game ->
        PanicHomeAlertDialog(
            onDismissRequest = { newGame = null },
            title = { Text("New ${game.label} game") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.hasSave(game)) Text("This replaces the current game. Best scores and achievements stay saved.")
                    if (game == ArcadeTitle.SUDOKU) {
                        Text("Puzzle difficulty", style = MaterialTheme.typography.labelLarge)
                        for (row in ArcadeLevels.indices.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { index ->
                                    FilterChip(selected = level == index, onClick = { level = index }, label = { Text(ArcadeLevels[index]) })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open(game, fresh = true); newGame = null }) { Text("New game") } },
            dismissButton = { TextButton(onClick = { newGame = null }) { Text("Cancel") } },
        )
    }
    help?.let { game ->
        PanicHomeAlertDialog(
            onDismissRequest = { help = null },
            title = { Text("${game.label} help") },
            text = {
                Text(when (game) {
                    ArcadeTitle.TILES -> "Swipe on the board to slide the tiles. Equal tiles merge once per move. Each successful move adds a 2 or 4. Undo also restores the previous random placement. Use a keyboard or turn on Arrow controls if you prefer buttons."
                    ArcadeTitle.SUDOKU -> "Fill each row, column and 3×3 block with 1–9. Tap a cell to select it, then enter a number or turn on Pencil to leave a note. Hint fills the selected editable cell. Arrow keys move the selection and number keys enter values."
                    ArcadeTitle.SNAKE -> "Swipe on the board, use a keyboard or turn on Arrow controls. Eat fruit and avoid the walls and your body. You can queue up to two turns, but cannot turn directly back. Change the speed in Snake settings."
                })
            },
            confirmButton = { TextButton(onClick = { help = null }) { Text("Done") } },
        )
    }
}

private fun ArcadeData.hasSave(game: ArcadeTitle): Boolean = when (game) {
    ArcadeTitle.TILES -> tiles != null
    ArcadeTitle.SUDOKU -> sudoku != null
    ArcadeTitle.SNAKE -> snake != null
}

@Composable
private fun ArcadeCatalogue(home: PreviewData?, model: ArcadeData, onOpen: (ArcadeTitle) -> Unit, onNew: (ArcadeTitle) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val expanded = maxWidth >= 660.dp
        Column(
            Modifier.widthIn(max = 960.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(shape = ArcadeShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Play at your pace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArcadeMetric("2048 best", model.bestTiles.toString(), Modifier.weight(1f))
                        ArcadeMetric("Sudoku solved", model.sudokuWins.toString(), Modifier.weight(1f))
                        ArcadeMetric("Snake best", model.bestSnake.toString(), Modifier.weight(1f))
                    }
                }
            }
            Text("Games", style = MaterialTheme.typography.titleLarge)
            val games = ArcadeTitle.entries
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    games.forEach { game -> ArcadeGameTile(game, home, model, onOpen, onNew, Modifier.weight(1f)) }
                }
            } else {
                games.forEach { game -> ArcadeGameTile(game, home, model, onOpen, onNew, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun ArcadeGameTile(
    game: ArcadeTitle,
    home: PreviewData?,
    model: ArcadeData,
    onOpen: (ArcadeTitle) -> Unit,
    onNew: (ArcadeTitle) -> Unit,
    modifier: Modifier,
) {
    val saved = model.hasSave(game)
    val shortcut = "arcade-${game.id}"
    val added = home?.profile?.pages?.any { shortcut in it } == true
    val progress = when (game) {
        ArcadeTitle.TILES -> "Best ${model.bestTiles}"
        ArcadeTitle.SUDOKU -> model.sudoku?.let { "${ArcadeLevels[it.level]} · ${(0..80).count { index -> it.cell(index) != 0 }}/81 filled" }
            ?: "${model.sudokuWins} solved"
        ArcadeTitle.SNAKE -> "Best ${model.bestSnake}"
    }
    Surface(modifier, shape = ArcadeShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ArcadeArtwork(game, Modifier.size(50.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(game.genre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(progress, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (game == ArcadeTitle.SUDOKU && !saved) onNew(game) else onOpen(game) },
                    modifier = Modifier.weight(1f).testTag(if (saved) "continue-${game.id}" else "play-${game.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text(if (saved) "Continue" else "New game") }
                if (saved) {
                    IconButton(onClick = { onNew(game) }) { Icon(Icons.Default.Refresh, "New ${game.label} game") }
                }
                if (home != null) {
                    IconButton(onClick = { home.addHome(shortcut) }, modifier = Modifier.testTag("shortcut-${game.id}")) {
                        Icon(if (added) Icons.Default.Check else Icons.Default.Add, if (added) "${game.label} on Home" else "Add ${game.label} to Home")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArcadeArtwork(game: ArcadeTitle, modifier: Modifier) {
    val accent = game.accent
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(
        Brush.linearGradient(listOf(accent.copy(alpha = .36f), Color(0xFF20263E))),
    )) {
        val unit = size.width / 10f
        val radius = CornerRadius(unit * .55f)
        when (game) {
            ArcadeTitle.TILES -> {
                for ((i, alpha) in listOf(.35f, .65f, .84f, 1f).withIndex()) {
                    drawRoundRect(accent.copy(alpha = alpha), Offset((1.8f + i % 2 * 3.3f) * unit, (1.8f + i / 2 * 3.3f) * unit), Size(unit * 2.9f, unit * 2.9f), radius)
                }
            }
            ArcadeTitle.SUDOKU -> {
                for (row in 0..2) for (column in 0..2) {
                    val strong = row == column || row + column == 3
                    drawRoundRect(accent.copy(alpha = if (strong) .9f else .28f), Offset((1.5f + column * 2.4f) * unit, (1.5f + row * 2.4f) * unit), Size(unit * 2.05f, unit * 2.05f), CornerRadius(unit * .3f))
                }
            }
            ArcadeTitle.SNAKE -> {
                val points = listOf(2f to 7f, 4f to 7f, 6f to 7f, 6f to 5f, 6f to 3f)
                points.forEachIndexed { index, (x, y) ->
                    drawRoundRect(accent.copy(alpha = .46f + index * .13f), Offset(x * unit, y * unit), Size(unit * 1.9f, unit * 1.9f), radius)
                }
                drawCircle(Color(0xFFFFA8AD), unit * .75f, Offset(3.25f * unit, 3.4f * unit))
                drawCircle(Color(0xFF163834), unit * .2f, Offset(7.35f * unit, 3.6f * unit))
            }
        }
    }
}

@Composable
private fun ArcadeAchievements(model: ArcadeData) {
    val tile = model.tiles?.cells()?.maxOrNull() ?: 0
    val awards = listOf(
        Triple("tile-2048", "Reach 2048", tile to 2048),
        Triple("sudoku-solved", "Solve a Sudoku", model.sudokuWins to 1),
        Triple("sudoku-no-hints", "Solve without hints", 0 to 1),
        Triple("snake-10", "Eat 10 fruit", model.bestSnake to 10),
        Triple("snake-25", "Eat 25 fruit", model.bestSnake to 25),
    )
    ArcadeSecondaryPage {
        Text("${awards.count { it.first in model.achievements }} of ${awards.size} earned", style = MaterialTheme.typography.headlineSmall)
        awards.forEachIndexed { index, (id, title, counts) ->
            val done = id in model.achievements
            val value = if (done) counts.second else counts.first.coerceAtMost(counts.second)
            Surface(shape = ArcadeShape, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Default.Check else Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(if (done) "Earned" else "$value / ${counts.second}", style = MaterialTheme.typography.labelLarge)
                    }
                    LinearProgressIndicator(
                        progress = { value.toFloat() / counts.second },
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.testTag("arcade-achievement-progress") else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcadeSettings(model: ArcadeData) {
    ArcadeSecondaryPage {
        Surface(shape = ArcadeShape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Game sounds", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(model.sound, { model.settings(sound = it) }, Modifier.semantics { contentDescription = "Game sounds" })
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Touch haptics", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(model.haptics, { model.settings(haptics = it) }, Modifier.semantics { contentDescription = "Touch haptics" })
                }
            }
        }
        Surface(shape = ArcadeShape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("Snake", style = MaterialTheme.typography.titleMedium)
                Text("Speed · ${model.speed} / 5", style = MaterialTheme.typography.labelLarge)
                Slider(model.speed.toFloat(), { model.settings(speed = it.roundToInt()) }, valueRange = 1f..5f, steps = 3,
                    modifier = Modifier.semantics { contentDescription = "Snake speed" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Relaxed", style = MaterialTheme.typography.labelMedium)
                    Text("Fast", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ArcadeSecondaryPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
