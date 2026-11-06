package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.calculator.*
import uk.co.traynor.privategallery.core.home2.SpaceContext

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun CalculatorScreen(active:Boolean=true,onHome:()->Unit) {
    val context=LocalContext.current as SpaceContext
    val loaded=remember(context){runCatching{CalculatorData(context)}}
    val model=loaded.getOrNull()
    if(model==null){Column(Modifier.fillMaxSize().padding(24.dp)){Text("Calculator could not open. Existing encrypted data is preserved.");TextButton(onClick=onHome){Text("Home")}};return}
    val lifecycle=LocalLifecycleOwner.current
    val activeNow=rememberUpdatedState(active)
    var resumed by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var epoch by remember{mutableIntStateOf(0)}
    val gesture=remember(context){CalculatorGesture(android.os.SystemClock::elapsedRealtime)}
    var live by remember{mutableStateOf(true)}
    var showHistory by remember{mutableStateOf(false)}
    var showHelp by remember{mutableStateOf(false)}
    var copied by remember{mutableStateOf(false)}
    DisposableEffect(context,lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME)resumed=true
            else if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){resumed=false;epoch++;gesture.reset();showHistory=false;showHelp=false;copied=false;model.retireInput()}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{live=false;epoch++;gesture.reset();model.retireInput();lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(active){if(!active){epoch++;gesture.reset();showHistory=false;showHelp=false;copied=false}}
    val capturedEpoch=epoch
    val current={live&&activeNow.value&&resumed&&epoch==capturedEpoch&&model.current()&&lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
    fun act(work:()->Unit){if(current()){gesture.reset();copied=false;work()}}
    val key:(String)->Unit={label->act{when(label){"AC"->model.clear();"⌫"->model.delete();"±"->model.sign();"="->model.evaluate();else->model.append(label)}}}
    val hold:()->Unit={
        if(current()&&gesture.hold()){
            val host=context.baseContext as? MainActivity
            if(host!=null)host.openHiddenFromCalculator(context,gesture,current) else gesture.reset()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=8.dp).testTag("calculator")) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Calculator",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton(onClick={act{showHelp=true}}){Text("Help")};TextButton(onClick={act(onHome)}){Text("Home")}}
        OutlinedTextField(value=model.expression,onValueChange={value->act{model.input(value)}},label={Text("Expression")},modifier=Modifier.fillMaxWidth().testTag("calculator-expression"),minLines=1,maxLines=3)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(model.result?.let(CalculatorEngine::format)?:"0",Modifier.weight(1f).padding(vertical=12.dp).testTag("calculator-result"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Medium)
            TextButton(enabled=model.result!=null,onClick={act{
                model.result?.let{result->
                    val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip=android.content.ClipData.newPlainText("Calculator result",CalculatorEngine.canonical(result))
                    clip.description.extras=android.os.PersistableBundle().apply{putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)}
                    if(current()){clipboard.setPrimaryClip(clip);copied=true}
                }
            }}){Text(if(copied)"Copied"else "Copy")}
        }
        model.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("calculator-error"))}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            FilterChip(model.scientific,{act{model.settings(scientific=!model.scientific)}},label={Text("Scientific")})
            Spacer(Modifier.width(8.dp));TextButton(onClick={act{model.settings(radians=!model.radians)}}){Text(if(model.radians)"RAD"else "DEG")}
            TextButton(onClick={act{showHistory=!showHistory}}){Text("History")}
            model.memory?.let{Text("M",Modifier.semantics{contentDescription="Memory contains a value"},fontWeight=FontWeight.Bold)}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){listOf("MC","MR","MS","M+","M−").forEach{action->TextButton(onClick={act{model.memory(action)}},modifier=Modifier.semantics{contentDescription=when(action){"MC"->"Clear memory";"MR"->"Recall memory";"MS"->"Store in memory";"M+"->"Add to memory";else->"Subtract from memory"}}){Text(action)}}}
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide=maxWidth>=600.dp
            val standard:@Composable ()->Unit={CalculatorKeys(listOf(listOf("AC","⌫","%","/"),listOf("7","8","9","*"),listOf("4","5","6","-"),listOf("1","2","3","+"),listOf("±","0",".","="),listOf("(",")")),key,hold)}
            val science:@Composable ()->Unit={CalculatorKeys(listOf(listOf("sin(","cos(","tan("),listOf("log(","ln(","sqrt("),listOf("^","!","pi"),listOf("e","E")),key,{})}
            if(wide&&model.scientific)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){Column(Modifier.weight(1f)){science()};Column(Modifier.weight(1f)){standard()}}
            else Column{if(model.scientific)science();standard()}
        }
        if(showHistory){
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Row(verticalAlignment=Alignment.CenterVertically){Text("Save history",Modifier.weight(1f));Switch(model.historyEnabled,{value->act{model.settings(historyEnabled=value)}},Modifier.semantics{contentDescription="Save calculation history"})}
            Text("History is private to this Space. Turning it off clears saved entries.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={act{model.clearHistory()}}){Text("Clear history")}
            if(model.history.isEmpty())Text("No saved calculations")
            model.history.forEachIndexed{index,entry->OutlinedButton(onClick={act{model.recall(entry);showHistory=false}},modifier=Modifier.fillMaxWidth().testTag("calculator-history-$index")){Column(Modifier.fillMaxWidth()){Text(entry.expression());Text("= ${CalculatorEngine.format(CalculatorEngine.storedNumber(entry.result()))} · ${if(entry.radians())"RAD"else "DEG"}")}}}
        }
    }
    if(showHelp&&current())PanicHomeAlertDialog(onDismissRequest={showHelp=false},title={Text("Calculator help")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("Percent always divides by 100: 10% = 0.1; 200 + 10% = 200.1. For a 10% increase use 200 × (1 + 10%).")
        Text("Parentheses first; factorial/percent, powers, sign, multiply/divide, then add/subtract. Powers associate right: 2^3^2 = 512; −2^2 = −4. Use explicit multiplication.")
        Text("Decimal arithmetic uses 34 significant digits; division rounds half-even. Scientific trig/log/constants and fractional powers are approximate. DEG and RAD select angle units. E enters a number exponent, for example 1E6.")
        Text("Expressions: 512 characters, 256 tokens and 32 nested levels. Powers: −1000 to 1000. Factorial: whole numbers 0 to 1000. Magnitudes: 10⁻¹⁰⁰⁰⁰ to 10¹⁰⁰⁰⁰. Trig angles up to 10⁹ radians. Undefined or out-of-range operations show an error.")
        Text("MS stores, MR recalls, M+ adds, M− subtracts and MC clears memory. Copy places only the result on the system clipboard. Panic does not erase a value already copied there. History keeps the latest 50 completed calculations in this Space.")
    }},confirmButton={TextButton(onClick={showHelp=false}){Text("Done")}})
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun CalculatorKeys(rows:List<List<String>>,onKey:(String)->Unit,onHold:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        rows.forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            row.forEach{label->
                val description=when(label){"AC"->"Clear all";"⌫"->"Delete last character";"±"->"Change sign";"*"->"Multiply";"/"->"Divide";"-"->"Subtract";"+"->"Add";"="->"Equals";"%"->"Percent";"("->"Open parenthesis";")"->"Close parenthesis";"^"->"Power";"!"->"Factorial";"sqrt("->"Square root";"sin("->"Sine";"cos("->"Cosine";"tan("->"Tangent";"log("->"Base ten logarithm";"ln("->"Natural logarithm";"pi"->"Pi";"e"->"Euler constant";"E"->"Number exponent";"."->"Decimal point";else->label}
                Surface(Modifier.weight(1f).heightIn(min=52.dp).testTag("calculator-key-$label")
                    .semantics{
                        contentDescription=description
                        // Capture this composition's authority rather than combinedClickable's
                        // updated callback holder; retained accessibility actions must expire.
                        onClick {onKey(label);true}
                        if(label=="=")onLongClick("Hold equals"){onHold();true}
                    }
                    .combinedClickable(role=Role.Button,onClick={onKey(label)},onLongClick=if(label=="=")onHold else null,onLongClickLabel=if(label=="=")"Hold equals"else null),shape=RoundedCornerShape(12.dp),color=if(label=="=")MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh){
                    Box(Modifier.padding(12.dp),contentAlignment=Alignment.Center){Text(when(label){"*"->"×";"/"->"÷";"pi"->"π";"sqrt("->"√";else->label.removeSuffix("(")},style=MaterialTheme.typography.titleLarge)}
                }
            }
        }}
    }
}

/** This component is only composed by independently authenticated Hidden Settings. */
@Composable internal fun HiddenCalculatorSettings(context:android.content.Context,session:uk.co.traynor.privategallery.core.hidden.HiddenSession,
    identity:uk.co.traynor.privategallery.core.hidden.HiddenSession.Authentication,current:()->Boolean) {
    val store=remember(context){uk.co.traynor.privategallery.core.hidden.HiddenCalculatorEntryStore(context)}
    var enabled by remember(identity){mutableStateOf(store.enabled())}
    var message by remember(identity){mutableStateOf<String?>(null)}
    val lifecycle=LocalLifecycleOwner.current
    var epoch by remember(identity){mutableIntStateOf(0)}
    var live by remember(identity){mutableStateOf(true)}
    DisposableEffect(identity,lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP)epoch++};lifecycle.lifecycle.addObserver(observer);onDispose{live=false;epoch++;lifecycle.lifecycle.removeObserver(observer)}}
    val captured=epoch
    val allowed={live&&epoch==captured&&current()&&session.isAuthenticationCurrent(identity)&&lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
    if(!allowed())return
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)){
        Text("Calculator entry",style=MaterialTheme.typography.titleMedium)
        Text("Optional: in Primary Calculator, long-press equals five times within 15 seconds. Any ordinary key resets the sequence. This opens the Hidden PIN screen and always requires your PIN. The gesture is never calculation history. Off by default; this setting stays on this device and is excluded from backups.")
        Row(verticalAlignment=Alignment.CenterVertically){Text("Enable calculator entry",Modifier.weight(1f));Switch(enabled,{value->
            if(allowed())runCatching{store.setEnabled(value,session,identity,allowed)}.onSuccess{enabled=value;message=null}.onFailure{message="Setting could not be saved. Unlock Hidden and try again."}
        },Modifier.testTag("calculator-entry-enabled"))}
        message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}
