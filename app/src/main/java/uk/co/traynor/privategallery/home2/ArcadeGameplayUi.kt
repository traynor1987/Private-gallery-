package uk.co.traynor.privategallery.home2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.traynor.privategallery.core.arcade.Game2048
import uk.co.traynor.privategallery.core.arcade.SnakeGame
import uk.co.traynor.privategallery.core.arcade.SudokuGame
import kotlin.math.abs

@Composable
internal fun ArcadeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Reserve room for the controls before choosing the square board size on the cover display. */
@Composable
private fun ArcadePlayLayout(
    controlsRoom: Dp,
    status: @Composable () -> Unit,
    board: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val fontScale = LocalDensity.current.fontScale
        if (maxWidth >= 600.dp && fontScale <= 1.35f) {
            val side = (maxWidth * .52f).coerceAtMost((maxHeight - 20.dp).coerceAtLeast(168.dp)).coerceAtMost(460.dp)
            Row(
                Modifier.widthIn(max = 1040.dp).fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                board(Modifier.size(side))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    status()
                    controls()
                }
            }
        } else {
            val room = (maxHeight - controlsRoom * fontScale.coerceAtLeast(1f) - 28.dp).coerceAtLeast(168.dp)
            val side = (maxWidth - 24.dp).coerceAtLeast(1.dp).coerceAtMost(room).coerceAtMost(440.dp)
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                status()
                board(Modifier.size(side))
                controls()
            }
        }
    }
}

@Composable
private fun ScoreCards(score: String, best: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ArcadeMetric("Score", score, Modifier.weight(1f))
        ArcadeMetric("Best", best, Modifier.weight(1f))
    }
}

@Composable
internal fun TilesGameplay(game: Game2048, best: Long, onMove: (Int) -> Unit, onUndo: () -> Unit, onRestart: () -> Unit) {
    var directions by remember { mutableStateOf(false) }
    ArcadePlayLayout(
        controlsRoom = if (directions) 172.dp else 124.dp,
        status = { ScoreCards(game.score.toString(), best.toString()) },
        board = { modifier -> TilesBoard(game, modifier, onMove) },
        controls = {
            if (game.over() || game.won()) {
                Text(
                    if (game.over()) "Game over · No moves remain" else "2048 reached · Keep going!",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onUndo, enabled = game.canUndo(), modifier = Modifier.weight(1f).testTag("tiles-undo"), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Undo")
                }
                FilledTonalButton(onClick = onRestart, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Restart")
                }
                ArrowToggle(directions, { directions = !directions }, Modifier.weight(1.3f))
            }
            if (directions) ArcadeDirectionKeys(false, onMove)
        },
    )
}

@Composable
private fun TilesBoard(game: Game2048, modifier: Modifier, onMove: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val frame = RoundedCornerShape(20.dp)
    Column(
        modifier.focusRequester(focus).arcadeGameInput(false, onMove).focusable().testTag("tiles-board")
            .background(Color(0xFF262138), frame).border(1.dp, Color(0xFF514460), frame).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(4) { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { column ->
                    val value = game.cell(row * 4 + column)
                    Tile(value, row, column, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

private val TilePalette = listOf(
    Color(0xFFF4DDBF), Color(0xFFFFC980), Color(0xFFFDA671), Color(0xFFF28D93),
    Color(0xFFE79DDA), Color(0xFFCAACF2), Color(0xFFA7BDF3), Color(0xFF91D2EF),
    Color(0xFF91E1D7), Color(0xFFB7E5A3), Color(0xFFFFE08B),
)

@Composable
private fun Tile(value: Int, row: Int, column: Int, modifier: Modifier) {
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        if (value != previous && value > 0) {
            pulse.snapTo(if (value > previous && previous > 0) .83f else .92f)
            pulse.animateTo(1f, tween(135))
        } else {
            pulse.snapTo(1f)
        }
        previous = value
    }
    val fill = if (value == 0) Color(0xFF383048) else TilePalette[(Integer.numberOfTrailingZeros(value) - 1).coerceIn(TilePalette.indices)]
    BoxWithConstraints(
        modifier.semantics { contentDescription = "Row ${row + 1}, column ${column + 1}, ${if (value == 0) "empty" else value}" },
        contentAlignment = Alignment.Center,
    ) {
        val digits = value.toString()
        val textSize = (maxWidth.value / (digits.length * .65f).coerceAtLeast(1.95f)).coerceIn(7f, 37f).sp
        Box(
            Modifier.fillMaxSize().graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .background(fill, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (value != 0) Text(digits, color = Color(0xFF281D32), fontSize = textSize, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
internal fun SnakeGameplay(
    game: SnakeGame, best: Int, onTurn: (Int) -> Unit, onArrowTurn: (Int) -> Unit, onPause: () -> Unit, onRestart: () -> Unit,
) {
    var directions by remember { mutableStateOf(false) }
    ArcadePlayLayout(
        controlsRoom = if (directions) 204.dp else 154.dp,
        status = { ScoreCards(game.score.toString(), best.toString()) },
        board = { modifier -> SnakeBoard(game, modifier, onTurn) },
        controls = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onPause, enabled = !game.over, modifier = Modifier.weight(1.6f).testTag("snake-pause"), contentPadding = PaddingValues(horizontal = 5.dp)) {
                    Text(if (game.paused) "Start / Resume" else "Pause", textAlign = TextAlign.Center)
                }
                FilledTonalButton(onClick = onRestart, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Restart")
                }
                ArrowToggle(directions, { directions = !directions }, Modifier.weight(1.25f))
            }
            if (directions) ArcadeDirectionKeys(true, onArrowTurn)
        },
    )
}

@Composable
private fun SnakeBoard(game: SnakeGame, modifier: Modifier, onMove: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val body = game.body()
    val state = when {
        game.over && game.food < 0 -> "Board complete"
        game.over -> "Game over"
        game.paused -> "Paused"
        else -> "Running"
    }
    val shape = RoundedCornerShape(20.dp)
    val fruitScale = remember { Animatable(1f) }
    LaunchedEffect(game.food) {
        if (game.score > 0 && game.food >= 0) {
            fruitScale.snapTo(.6f)
            fruitScale.animateTo(1f, tween(150))
        }
    }
    val badgeAlpha by animateFloatAsState(if (state == "Running") 0f else 1f, tween(120), label = "Snake status")
    Box(
        modifier.focusRequester(focus).arcadeGameInput(true, onMove).focusable().testTag("snake-board")
            .clip(shape).background(Color(0xFF0F242B)).border(1.dp, Color(0xFF365D63), shape)
            .semantics {
                val fruit = if (game.food >= 0) " Fruit row ${game.food / 16 + 1}, column ${game.food % 16 + 1}." else ""
                contentDescription = "Snake board. Head row ${body[0] / 16 + 1}, column ${body[0] % 16 + 1}.$fruit Score ${game.score}. $state."
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val cell = size.width / 16f
            for (i in 1 until 16) {
                val coordinate = i * cell
                val tint = if (i % 4 == 0) Color(0xFF26444D) else Color(0xFF1B343C)
                drawLine(tint, Offset(coordinate, 0f), Offset(coordinate, size.height))
                drawLine(tint, Offset(0f, coordinate), Offset(size.width, coordinate))
            }
            body.forEachIndexed { index, position ->
                val x = position % 16 * cell
                val y = position / 16 * cell
                val head = index == 0
                val shade = when {
                    head -> Color(0xFFA5F3D7)
                    index % 2 == 0 -> Color(0xFF4CA78C)
                    else -> Color(0xFF58B99E)
                }
                val inset = if (head) cell * .07f else cell * .1f
                drawRoundRect(shade, Offset(x + inset, y + inset), Size(cell - 2 * inset, cell - 2 * inset), CornerRadius(cell * .28f))
                if (head) {
                    val horizontal = game.direction % 2 == 0
                    val front = if (game.direction == 0 || game.direction == 1) .70f else .30f
                    for (side in listOf(.32f, .68f)) {
                        val eye = if (horizontal) Offset(x + cell * front, y + cell * side) else Offset(x + cell * side, y + cell * front)
                        drawCircle(Color(0xFF112F32), cell * .07f, eye)
                    }
                }
            }
            if (game.food >= 0) {
                val center = Offset((game.food % 16 + .5f) * cell, (game.food / 16 + .5f) * cell)
                val radius = cell * .32f * fruitScale.value
                drawCircle(Color(0x33FFA4AA), radius * 1.45f, center)
                drawCircle(Color(0xFFFFA4AA), radius, center)
                drawCircle(Color(0xFFFFE7E6), radius * .25f, center - Offset(radius * .3f, radius * .32f))
            }
        }
        if (state != "Running" || badgeAlpha > 0f) {
            Surface(
                Modifier.padding(top = 12.dp).graphicsLayer { alpha = badgeAlpha },
                shape = RoundedCornerShape(14.dp),
                color = Color(0xE1223844),
                contentColor = Color(0xFFE0FFF4),
            ) {
                Text(state, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ArrowToggle(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).testTag("arcade-directions").semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 3.dp),
    ) {
        Text(if (selected) "Hide arrows" else "Arrow controls", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ArcadeDirectionKeys(snake: Boolean, onMove: (Int) -> Unit) {
    val arrows = listOf(
        Triple("←", "Move left", if (snake) 2 else 0),
        Triple("↑", "Move up", 3),
        Triple("↓", "Move down", 1),
        Triple("→", "Move right", if (snake) 0 else 2),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        arrows.forEach { (arrow, label, direction) ->
            FilledTonalButton(
                onClick = { onMove(direction) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = label },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text(arrow, fontSize = 22.sp) }
        }
    }
}

@Composable
internal fun SudokuGameplay(game: SudokuGame, onSave: (SudokuGame) -> Unit, onRestart: () -> Unit) {
    var selected by remember { mutableIntStateOf((0 until 81).firstOrNull { game.given(it) == 0 } ?: 0) }
    var pencil by remember { mutableStateOf(false) }
    val editable = !game.paused && !game.complete()
    ArcadePlayLayout(
        controlsRoom = 246.dp,
        status = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val time = "${game.seconds / 60}:${(game.seconds % 60).toString().padStart(2, '0')}"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ArcadeMetric("Level", ArcadeLevels[game.level], Modifier.weight(1.2f))
                    ArcadeMetric("Time", time, Modifier.weight(1f))
                    ArcadeMetric("Mistakes", game.mistakes.toString(), Modifier.weight(1f))
                    ArcadeMetric("Hints", game.hints.toString(), Modifier.weight(.8f))
                }
                Text(
                    when {
                        game.complete() -> "Puzzle solved!"
                        game.paused -> "Paused"
                        else -> "Row ${selected / 9 + 1} · Column ${selected % 9 + 1}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        board = { modifier ->
            SudokuBoard(game, selected, pencil, modifier, onSelect = { selected = it }, onSave)
        },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                SudokuTool("Undo", Icons.AutoMirrored.Filled.Undo, enabled = !game.paused && game.canUndo(), modifier = Modifier.weight(1f)) { onSave(game.undo()) }
                SudokuTool("Erase", Icons.Default.Backspace, enabled = editable, modifier = Modifier.weight(1f)) { onSave(game.enter(selected, 0, false)) }
                SudokuTool("Pencil", Icons.Default.Edit, enabled = editable, selected = pencil, modifier = Modifier.weight(1f)) { pencil = !pencil }
                SudokuTool("Hint", Icons.Default.Lightbulb, enabled = editable, modifier = Modifier.weight(1f)) { onSave(game.hint(selected)) }
                SudokuTool(if (game.paused) "Resume" else "Pause", if (game.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    enabled = !game.complete(), modifier = Modifier.weight(1f).testTag("sudoku-pause")) { onSave(game.pause(!game.paused)) }
            }
            SudokuKeypad(editable) { onSave(game.enter(selected, it, pencil)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = (selected + 80) % 81 }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                    Text("Previous cell", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { selected = (selected + 1) % 81 }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                    Text("Next cell", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onRestart, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) {
                    Text("New puzzle", textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
    )
}

@Composable
private fun SudokuTool(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(11.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = when {
            !enabled -> colors.onSurface.copy(alpha = .52f)
            selected -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
    ) {
        Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SudokuKeypad(enabled: Boolean, onNumber: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rows = if (maxWidth >= 464.dp) listOf(1..9) else listOf(1..5, 6..9)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (numbers in rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    numbers.forEach { number ->
                        FilledTonalButton(
                            onClick = { onNumber(number) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sudoku-number-$number"),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("$number", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SudokuBoard(game: SudokuGame, selected: Int, pencil: Boolean, modifier: Modifier, onSelect: (Int) -> Unit, onSave: (SudokuGame) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val hidden = game.paused && !game.complete()
    val frame = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        modifier.focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> onSelect((selected - 1).coerceAtLeast(0))
                Key.DirectionRight -> onSelect((selected + 1).coerceAtMost(80))
                Key.DirectionUp -> onSelect((selected - 9).coerceAtLeast(0))
                Key.DirectionDown -> onSelect((selected + 9).coerceAtMost(80))
                Key.Backspace, Key.Delete -> onSave(game.enter(selected, 0, false))
                else -> {
                    val number = event.nativeKeyEvent.unicodeChar.toChar().digitToIntOrNull()
                    if (number == null || number !in 1..9) return@onPreviewKeyEvent false
                    onSave(game.enter(selected, number, pencil))
                }
            }
            true
        }.focusable().testTag("sudoku-board").clip(frame).border(1.dp, Color(0xFF8A79AB), frame),
    ) {
        val fontScale = LocalDensity.current.fontScale
        val numeralSize = (maxWidth.value / 9f * .56f / fontScale).coerceIn(9f, 26f).sp
        val noteSize = (maxWidth.value / 9f * .23f / fontScale).coerceIn(5f, 10f).sp
        Column(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            for (i in 1..8) {
                val x = size.width * i / 9f
                val y = size.height * i / 9f
                val strong = i % 3 == 0
                val line = if (strong) Color(0xFF9C8BBC) else Color(0xFF46506D)
                val weight = (if (strong) 1.8.dp else .6.dp).toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), weight)
                drawLine(line, Offset(0f, y), Offset(size.width, y), weight)
            }
        }) {
            repeat(9) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(9) { column ->
                        val index = row * 9 + column
                        val value = game.cell(index)
                        val fixed = game.given(index) != 0
                        val wrong = game.wrong(index) && !hidden
                        val noteDigits = (1..9).filter { game.note(index) and (1 shl it) != 0 }
                        val highlight = !hidden && (row == selected / 9 || column == selected % 9)
                        val selectedCell = index == selected && !hidden
                        val fill = when {
                            selectedCell -> Color(0xFF5E4E91)
                            highlight -> Color(0xFF323D63)
                            (row / 3 + column / 3) % 2 == 0 -> Color(0xFF1A223A)
                            else -> Color(0xFF222943)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(fill).clickable(enabled = !game.paused) { onSelect(index) }
                                .semantics {
                                    val contents = when {
                                        hidden -> "paused"
                                        value != 0 -> "$value"
                                        noteDigits.isNotEmpty() -> "empty, notes ${noteDigits.joinToString()}"
                                        else -> "empty"
                                    }
                                    contentDescription = "Row ${row + 1}, column ${column + 1}, $contents${if (fixed && !hidden) ", fixed" else ""}${if (wrong) ", incorrect" else ""}"
                                    this.selected = selectedCell
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                hidden -> Text("·", color = Color(0xFF8995BA), fontSize = numeralSize)
                                value != 0 -> Text(
                                    "$value",
                                    color = when {
                                        wrong -> Color(0xFFFFB6C0)
                                        fixed -> Color(0xFFF5F3FF)
                                        else -> Color(0xFFB6F0DE)
                                    },
                                    fontWeight = if (fixed) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = numeralSize,
                                    lineHeight = numeralSize,
                                )
                                noteDigits.isNotEmpty() -> Text(
                                    (1..9).chunked(3).joinToString("\n") { digits -> digits.joinToString(" ") { digit -> if (digit in noteDigits) "$digit" else "·" } },
                                    color = Color(0xFFC4CFED),
                                    textAlign = TextAlign.Center,
                                    fontSize = noteSize,
                                    lineHeight = noteSize,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.arcadeGameInput(snake: Boolean, onMove: (Int) -> Unit): Modifier {
    val move by rememberUpdatedState(onMove)
    val left = if (snake) 2 else 0
    val right = if (snake) 0 else 2
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val direction = when (event.key) {
            Key.DirectionLeft -> left
            Key.DirectionDown -> 1
            Key.DirectionRight -> right
            Key.DirectionUp -> 3
            else -> return@onPreviewKeyEvent false
        }
        move(direction)
        true
    }.pointerInput(snake) {
        var drag = Offset.Zero
        detectDragGestures(
            onDragStart = { drag = Offset.Zero },
            onDragCancel = { drag = Offset.Zero },
            onDragEnd = {
                if (abs(drag.x) > 32 || abs(drag.y) > 32) {
                    val direction = if (abs(drag.x) > abs(drag.y)) {
                        if (drag.x > 0) right else left
                    } else {
                        if (drag.y > 0) 1 else 3
                    }
                    move(direction)
                }
                drag = Offset.Zero
            },
        ) { change, amount ->
            change.consume()
            drag += amount
        }
    }
}
