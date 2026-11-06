package uk.co.traynor.privategallery.home2

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.arcade.*
import uk.co.traynor.privategallery.core.home2.SpaceContext
import kotlin.math.abs

private val gameNames=mapOf("2048" to "2048","sudoku" to "Sudoku","snake" to "Snake")
private val awardNames=mapOf("tile-2048" to "Reach 2048","sudoku-solved" to "Solve a Sudoku","sudoku-no-hints" to "Solve without hints","snake-10" to "Eat 10 fruit","snake-25" to "Eat 25 fruit")
private val levels=listOf("Easy","Medium","Hard","Expert")

@Composable internal fun ArcadeScreen(home:PreviewData,initialGame:String?=null,active:Boolean=true,onHome:()->Unit){
 val context=LocalContext.current as SpaceContext
 val loaded=remember(context){runCatching{ArcadeData(context)}}
 val model=loaded.getOrNull()
 if(model==null){Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)){PreviewHeader("Nova Arcade","Saved data could not be read",onHome);Text("Your existing encrypted saves have been preserved. Lock and reopen this Space, or restore a verified backup.")};return}
 var page by remember{mutableStateOf(initialGame?.takeIf{it in gameNames}?:"library")}
 var restart by remember{mutableStateOf(false)}
 var level by remember{mutableIntStateOf(model.sudoku?.level?:0)}
 val lifecycle=LocalLifecycleOwner.current
 var resumed by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
 val view=LocalView.current
 val tone=remember{runCatching{ToneGenerator(AudioManager.STREAM_MUSIC,25)}.getOrNull()}
 val feedback:()->Unit={if(active&&resumed&&model.current()){if(model.sound)tone?.startTone(ToneGenerator.TONE_PROP_BEEP,45);if(model.haptics)view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)}}
 fun open(id:String){model.pause();if((id=="2048"&&model.tiles==null)||(id=="sudoku"&&model.sudoku==null)||(id=="snake"&&model.snake==null))model.start(id,level);model.visit(id);page=id}
 LaunchedEffect(initialGame){if(initialGame==null){model.pause();page="library"}else initialGame.takeIf{it in gameNames}?.let{open(it)}}
 DisposableEffect(lifecycle,model){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)resumed=true else if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){resumed=false;model.pause()}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);model.pause()}}
 DisposableEffect(tone){onDispose{tone?.release()}}
 LaunchedEffect(active){if(!active)model.pause()}
 LaunchedEffect(page,active,resumed,model.speed){while(active&&resumed&&model.current()){
  delay(if(page=="snake")(440L-model.speed*60L)else 1000L)
  if(!active||!resumed||!model.current())break
  if(page=="snake")model.snake?.takeIf{!it.paused&&!it.over}?.let{val next=it.tick();model.save(next);if(next.score>it.score||next.over)feedback()}
  if(page=="sudoku")model.sudoku?.takeIf{!it.paused&&!it.complete()}?.let{model.save(it.tick())}
 }}
 val back:()->Unit={model.pause();if(page=="library")onHome()else page="library"}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-arcade")){
  PreviewHeader(if(page in gameNames)gameNames.getValue(page)else "Nova Arcade","Offline · saved in this Space",back)
  model.error?.let{Text(it,Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.error)}
  when(page){
   "library"->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("Your arcade",style=MaterialTheme.typography.headlineSmall)
    Text("Three games, ready to play. No accounts, ads or downloads.")
    if(model.recent.isNotEmpty()){Text("Continue / recent",style=MaterialTheme.typography.titleMedium);model.recent.forEach{id->OutlinedButton(onClick={open(id)},modifier=Modifier.fillMaxWidth().testTag("continue-$id")){Text("Continue ${gameNames[id]}")}}}
    Text("Installed games",style=MaterialTheme.typography.titleMedium)
    gameNames.forEach{(id,name)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
     Text(name,style=MaterialTheme.typography.titleLarge)
     Text(when(id){"2048"->"Slide and merge · best ${model.bestTiles}";"sudoku"->"Four levels · ${model.sudokuWins} solved";else->"Steer, eat, survive · best ${model.bestSnake}"})
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Button(onClick={open(id)},modifier=Modifier.testTag("play-$id")){Text("Play")};TextButton(onClick={home.addHome("arcade-$id")},modifier=Modifier.testTag("shortcut-$id")){Text(if(home.profile.pages.any{"arcade-$id" in it})"On Home"else "Add to Home")}}
    }}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Chess · deferred",style=MaterialTheme.typography.titleMedium);Text("Not installed. A complete rules engine is planned for a later release.")}}
    Text("Scores & achievements",style=MaterialTheme.typography.titleMedium)
    Text("2048 best: ${model.bestTiles} · Snake best: ${model.bestSnake}\nSudoku solved: ${model.sudokuWins}")
    awardNames.forEach{(id,label)->Text("${if(id in model.achievements)"✓"else "○"} $label")}
    Text("Settings",style=MaterialTheme.typography.titleMedium)
    Row(verticalAlignment=Alignment.CenterVertically){Text("Game sounds",Modifier.weight(1f));Switch(model.sound,{model.settings(sound=it)},Modifier.semantics{contentDescription="Game sounds"})}
    Row(verticalAlignment=Alignment.CenterVertically){Text("Touch haptics",Modifier.weight(1f));Switch(model.haptics,{model.settings(haptics=it)},Modifier.semantics{contentDescription="Touch haptics"})}
    Text("Snake speed · ${model.speed} / 5");Slider(model.speed.toFloat(),{model.settings(speed=it.toInt())},valueRange=1f..5f,steps=3,modifier=Modifier.semantics{contentDescription="Snake speed"})
   }
   "2048"->model.tiles?.let{game->ArcadeLayout(board={TilesBoard(game){direction->if(active&&resumed){val next=game.move(direction);if(next!==game){model.save(next);feedback()}}}},controls={
    Text("Score ${game.score} · Best ${model.bestTiles}",style=MaterialTheme.typography.titleMedium)
    Text(if(game.over())"Game over — no moves remain."else if(game.won())"2048 reached! Keep going as far as you can."else "Swipe, use arrow keys or tap the direction buttons.")
    DirectionPad(false){d->val next=game.move(d);if(next!==game){model.save(next);feedback()}}
    Row{OutlinedButton(onClick={model.save(game.undo())},enabled=game.canUndo(),modifier=Modifier.testTag("tiles-undo")){Text("Undo")};TextButton(onClick={restart=true}){Text("Restart")}}
    Text("Merge equal tiles once per move. A successful move adds a 2 (90%) or 4 (10%). Undo restores the previous random placement too.",style=MaterialTheme.typography.bodySmall)
   })}
   "sudoku"->model.sudoku?.let{game->SudokuPanel(game,level,{level=it},{model.save(it);feedback()},{restart=true})}
   "snake"->model.snake?.let{game->ArcadeLayout(board={SnakeBoard(game){d->if(active&&resumed)model.snake?.let{model.save(it.turn(d))}}},controls={
    Text("Score ${game.score} · Best ${model.bestSnake}",style=MaterialTheme.typography.titleMedium)
    Text(if(game.over)if(game.body().size==256)"Board complete!"else "Game over — collision."else if(game.paused)"Paused"else "Running")
    Button(onClick={model.save(game.pause(!game.paused))},enabled=!game.over,modifier=Modifier.testTag("snake-pause")){Text(if(game.paused)"Start / Resume"else "Pause")}
    DirectionPad(true){d->model.snake?.let{model.save(it.turn(d))};feedback()}
    Text("Speed ${model.speed} / 5");Slider(model.speed.toFloat(),{model.settings(speed=it.toInt())},valueRange=1f..5f,steps=3,modifier=Modifier.semantics{contentDescription="Snake speed"})
    TextButton(onClick={restart=true}){Text("Restart")}
    Text("Swipe, use arrow keys or tap arrows. Eat fruit and avoid walls and your body. Two turns can be queued; reversals are refused.",style=MaterialTheme.typography.bodySmall)
   })}
  }
 }
 if(restart)PanicHomeAlertDialog(onDismissRequest={restart=false},title={Text("Start a new ${gameNames[page]} game?")},text={Text("This replaces the current game. Your best scores and achievements remain." )},confirmButton={TextButton(onClick={model.start(page,level);restart=false}){Text("New game")}},dismissButton={TextButton(onClick={restart=false}){Text("Cancel")}})
}

@Composable private fun ArcadeLayout(board:@Composable ()->Unit,controls:@Composable ColumnScope.()->Unit){
 BoxWithConstraints(Modifier.fillMaxSize()){
  if(maxWidth>=650.dp)Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)){
   Box(Modifier.weight(1f).widthIn(max=480.dp)){board()};Column(Modifier.weight(.8f),verticalArrangement=Arrangement.spacedBy(12.dp),content=controls)
  }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   Box(Modifier.fillMaxWidth().widthIn(max=480.dp).align(Alignment.CenterHorizontally)){board()};Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=controls)
  }
 }
}

@Composable private fun Modifier.gameInput(snake:Boolean,onMove:(Int)->Unit):Modifier {val move by rememberUpdatedState(onMove);return onPreviewKeyEvent{event->if(event.type!=KeyEventType.KeyDown)false else{val d=when(event.key){Key.DirectionLeft->if(snake)2 else 0;Key.DirectionDown->1;Key.DirectionRight->if(snake)0 else 2;Key.DirectionUp->3;else->-1};if(d>=0){move(d);true}else false}}.pointerInput(snake){var drag=Offset.Zero;detectDragGestures(onDragStart={drag=Offset.Zero},onDragCancel={drag=Offset.Zero},onDragEnd={if(abs(drag.x)>32||abs(drag.y)>32){val d=if(abs(drag.x)>abs(drag.y)){if(drag.x>0){if(snake)0 else 2}else{if(snake)2 else 0}}else if(drag.y>0)1 else 3;move(d)};drag=Offset.Zero}){change,amount->change.consume();drag+=amount}}}


@Composable private fun TilesBoard(game:Game2048,onMove:(Int)->Unit){
 val focus=remember{FocusRequester()}
 LaunchedEffect(Unit){focus.requestFocus()}
 Column(Modifier.fillMaxWidth().aspectRatio(1f).focusRequester(focus).gameInput(false,onMove).focusable().testTag("tiles-board").background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(16.dp)).padding(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  repeat(4){row->Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(4){col->val v=game.cell(row*4+col);Box(Modifier.weight(1f).fillMaxHeight().background(if(v==0)MaterialTheme.colorScheme.surface else if(v>=2048)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(10.dp)).semantics{contentDescription="Row ${row+1}, column ${col+1}, ${if(v==0)"empty"else v}"},contentAlignment=Alignment.Center){Text(if(v==0)""else "$v",fontSize=if(v>=1_000_000)10.sp else if(v>=16384)16.sp else 28.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)}}}}
 }
}

@Composable private fun DirectionPad(snake:Boolean,onMove:(Int)->Unit){
 Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()){
  OutlinedButton(onClick={onMove(3)},modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics{contentDescription="Move up"}){Text("↑")}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(onClick={onMove(if(snake)2 else 0)},modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics{contentDescription="Move left"}){Text("←")}
   OutlinedButton(onClick={onMove(1)},modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics{contentDescription="Move down"}){Text("↓")}
   OutlinedButton(onClick={onMove(if(snake)0 else 2)},modifier=Modifier.sizeIn(minWidth=64.dp,minHeight=48.dp).semantics{contentDescription="Move right"}){Text("→")}
  }
 }
}

@Composable private fun SudokuPanel(game:SudokuGame,level:Int,onLevel:(Int)->Unit,onSave:(SudokuGame)->Unit,onRestart:()->Unit){
 var selected by remember{mutableIntStateOf((0 until 81).firstOrNull{game.given(it)==0}?:0)}
 var pencil by remember{mutableStateOf(false)}
 val focus=remember{FocusRequester()}
 LaunchedEffect(Unit){focus.requestFocus()}
 ArcadeLayout(board={Column(Modifier.fillMaxWidth().aspectRatio(1f).focusRequester(focus).onPreviewKeyEvent{event->if(event.type!=KeyEventType.KeyDown)false else{when(event.key){Key.DirectionLeft->selected=(selected-1).coerceAtLeast(0);Key.DirectionRight->selected=(selected+1).coerceAtMost(80);Key.DirectionUp->selected=(selected-9).coerceAtLeast(0);Key.DirectionDown->selected=(selected+9).coerceAtMost(80);Key.Backspace,Key.Delete->onSave(game.enter(selected,0,false));else->{val n=event.nativeKeyEvent.unicodeChar.toChar().digitToIntOrNull();if(n!=null&&n in 1..9)onSave(game.enter(selected,n,pencil))else return@onPreviewKeyEvent false}};true}}.focusable().testTag("sudoku-board")){
  repeat(9){r->Row(Modifier.weight(1f)){repeat(9){c->val at=r*9+c;val v=game.cell(at);val note=(1..9).filter{game.note(at) and (1 shl it)!=0}.joinToString("");val noteGrid=(1..9).chunked(3).joinToString("\n"){group->group.joinToString(" "){n->if(game.note(at) and (1 shl n)!=0)"$n"else "·"}};val border=if(c%3==0||r%3==0)1.dp else .4.dp
   Box(Modifier.weight(1f).fillMaxHeight().border(border,MaterialTheme.colorScheme.outline).background(if(at==selected)MaterialTheme.colorScheme.primaryContainer else if(game.given(at)!=0)MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface).clickable(enabled=!game.paused){selected=at}.semantics{contentDescription="Row ${r+1}, column ${c+1}, ${if(game.paused&&!game.complete())"paused"else if(v==0)"empty, notes $note"else "$v"}${if(game.given(at)!=0)", fixed"else ""}${if(game.wrong(at))", incorrect"else ""}";this.selected=at==selected},contentAlignment=Alignment.Center){Text(if(game.paused&&!game.complete())"·"else if(v==0&&note.isNotEmpty())noteGrid else if(v==0)""else "$v",fontSize=if(v==0)9.sp else 21.sp,lineHeight=if(v==0)9.sp else 24.sp,textAlign=TextAlign.Center,fontWeight=if(game.given(at)!=0)FontWeight.Bold else FontWeight.Normal,color=if(game.wrong(at))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
  }}}
 }},controls={
  Text("${levels[game.level]} · ${game.seconds/60}:${(game.seconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.titleMedium)
  Text(if(game.complete())"Puzzle solved!"else if(game.paused)"Paused"else "Row ${selected/9+1}, column ${selected%9+1} selected")
  Text("Mistakes ${game.mistakes} · Hints ${game.hints}")
  Button(onClick={onSave(game.pause(!game.paused))},enabled=!game.complete(),modifier=Modifier.testTag("sudoku-pause")){Text(if(game.paused)"Resume"else "Pause")}
  for(numbers in listOf(1..5,6..9))Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){numbers.forEach{n->OutlinedButton(onClick={onSave(game.enter(selected,n,pencil))},enabled=!game.paused&&!game.complete(),modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("sudoku-number-$n"),contentPadding=PaddingValues(0.dp)){Text("$n")}}}
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(pencil,{pencil=it});Text("Pencil notes")}
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){TextButton(onClick={onSave(game.enter(selected,0,false))},enabled=!game.paused){Text("Erase")};TextButton(onClick={onSave(game.undo())},enabled=game.canUndo()&&!game.paused){Text("Undo")};TextButton(onClick={onSave(game.hint(selected))},enabled=!game.paused){Text("Hint")}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={selected=(selected+80)%81}){Text("Previous cell")};TextButton(onClick={selected=(selected+1)%81}){Text("Next cell")}}
  Text("New puzzle level");Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){levels.forEachIndexed{i,name->FilterChip(level==i,{onLevel(i)},label={Text(name)},modifier=Modifier.padding(end=4.dp))}}
  TextButton(onClick=onRestart){Text("New puzzle")}
  Text("Levels use fewer starting clues. Every puzzle has exactly one validated solution. Arrow keys select cells; number keys enter values. Hints fill the selected editable cell.",style=MaterialTheme.typography.bodySmall)
 })
}

@Composable private fun SnakeBoard(game:SnakeGame,onMove:(Int)->Unit){
 val focus=remember{FocusRequester()};LaunchedEffect(Unit){focus.requestFocus()}
 val body=game.body();val headColor=MaterialTheme.colorScheme.primary;val bodyColor=MaterialTheme.colorScheme.secondary;val foodColor=MaterialTheme.colorScheme.error;val background=MaterialTheme.colorScheme.surfaceVariant
 Canvas(Modifier.fillMaxWidth().aspectRatio(1f).focusRequester(focus).gameInput(true,onMove).focusable().testTag("snake-board").semantics{contentDescription="Snake board. Head row ${body[0]/16+1}, column ${body[0]%16+1}. Fruit row ${game.food/16+1}, column ${game.food%16+1}. Score ${game.score}. ${if(game.paused)"Paused"else "Running"}"}){
  drawRect(background);val cell=size.width/16
  body.forEachIndexed{i,at->drawRoundRect(if(i==0)headColor else bodyColor,Offset(at%16*cell+1,at/16*cell+1),androidx.compose.ui.geometry.Size(cell-2,cell-2),androidx.compose.ui.geometry.CornerRadius(cell*.2f))}
  if(game.food>=0)drawCircle(foodColor,cell*.35f,Offset((game.food%16+.5f)*cell,(game.food/16+.5f)*cell))
 }
}
