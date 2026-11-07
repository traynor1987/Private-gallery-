package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
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
    var panel by remember{mutableStateOf<CalculatorPanel?>(null)}
    var showHelp by remember{mutableStateOf(false)}
    var copied by remember{mutableStateOf(false)}
    DisposableEffect(context,lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_RESUME)resumed=true
            else if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){resumed=false;epoch++;gesture.reset();panel=null;showHelp=false;copied=false;model.retireInput()}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{live=false;epoch++;gesture.reset();model.retireInput();lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(active){if(!active){epoch++;gesture.reset();panel=null;showHelp=false;copied=false}}
    val capturedEpoch=epoch
    val current={live&&activeNow.value&&resumed&&epoch==capturedEpoch&&model.current()&&lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
    fun act(work:()->Unit){if(current()){gesture.reset();copied=false;work()}}
    val focusManager=LocalFocusManager.current
    val key: (String) -> Unit = { label -> act {
        focusManager.clearFocus()
        when(label){
            "AC"->model.clear()
            "⌫"->model.delete()
            "±"->model.sign()
            "="->model.evaluate()
            else->model.append(label)
        }
    }}
    val hold:()->Unit={
        if(current()&&gesture.hold()){
            val host=context.baseContext as? MainActivity
            if(host!=null)host.openHiddenFromCalculator(context,gesture,current) else gesture.reset()
        }
    }
    val copyResult: () -> Unit = {act {
        model.result?.let { result ->
            val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip=android.content.ClipData.newPlainText("Calculator result",CalculatorEngine.canonical(result))
            clip.description.extras=android.os.PersistableBundle().apply{putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)}
            if(current()){clipboard.setPrimaryClip(clip);copied=true}
        }
    }}
    val readout: @Composable () -> Unit = {
        CalculatorReadout(
            model=model,
            copied=copied,
            onHome={act(onHome)},
            onHelp={act{showHelp=true}},
            onInput={value->act{model.input(value)}},
            onCopy=copyResult,
            onScientific={act{model.settings(scientific=!model.scientific)}},
            onAngle={act{model.settings(radians=!model.radians)}},
            onMemory={act{panel=CalculatorPanel.Memory}},
            onHistory={act{panel=CalculatorPanel.History}},
        )
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("calculator").background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentAlignment=Alignment.TopCenter,
    ) {
        val wide=maxWidth>=650.dp
        val separateScientificColumn=maxWidth>=760.dp
        val short=maxHeight<440.dp
        val keyHeight=when {
            short->48.dp
            maxHeight>=660.dp->60.dp
            else->50.dp
        }
        val layout=Modifier.widthIn(max=1060.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min=maxHeight).padding(horizontal=if(wide)24.dp else 14.dp,vertical=8.dp)
        if(wide){
            Row(layout,horizontalArrangement=Arrangement.spacedBy(22.dp),verticalAlignment=Alignment.Bottom){
                Column(Modifier.weight(if(model.scientific)1f else .85f).align(Alignment.Top)){readout()}
                if(model.scientific&&separateScientificColumn){
                    CalculatorScienceKeys(key,keyHeight,Modifier.weight(.8f))
                    CalculatorStandardKeys(key,hold,keyHeight,sideParentheses=short,modifier=Modifier.weight(1.35f))
                }else{
                    Column(Modifier.weight(1.15f),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        if(model.scientific)CalculatorScienceKeys(key,keyHeight)
                        CalculatorStandardKeys(key,hold,keyHeight,sideParentheses=short)
                    }
                }
            }
        }else{
            Column(layout){
                readout()
                Spacer(Modifier.heightIn(min=12.dp).weight(1f))
                if(model.scientific){
                    CalculatorScienceKeys(key,keyHeight)
                    Spacer(Modifier.height(10.dp))
                }
                CalculatorStandardKeys(key,hold,keyHeight,sideParentheses=false)
            }
        }
    }
    if(current())when(panel){
        CalculatorPanel.Memory -> CalculatorPanelDialog("Memory",onClose={panel=null}){
            Text(if(model.memory==null)"Memory is empty" else "One value saved in this Space",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            memoryCommands.forEach { (action,description) ->
                OutlinedButton(
                    onClick={act{model.memory(action);panel=null}},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription=description},
                    shape=RoundedCornerShape(16.dp),
                ){Text(description,Modifier.weight(1f));Text(action,color=MaterialTheme.colorScheme.primary)}
            }
        }
        CalculatorPanel.History -> CalculatorPanelDialog("History",onClose={panel=null}){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("Save history",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                Switch(model.historyEnabled,{value->act{model.settings(historyEnabled=value)}},
                    Modifier.semantics{contentDescription="Save calculation history"})
            }
            Text("History is private to this Space. Turning it off clears saved entries.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(model.history.isEmpty()){
                Text("No saved calculations",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.titleMedium)
            }else{
                TextButton(onClick={act{model.clearHistory()}}){Text("Clear history")}
                model.history.forEachIndexed{index,entry->
                    Surface(
                        onClick={act{model.recall(entry);panel=null}},
                        modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("calculator-history-$index"),
                        shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,
                    ){
                        Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.End){
                            Text(entry.expression(),style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.End)
                            Text("= ${CalculatorEngine.format(CalculatorEngine.storedNumber(entry.result()))} · ${if(entry.radians())"RAD"else "DEG"}",
                                style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,textAlign=TextAlign.End)
                        }
                    }
                }
            }
        }
        null -> Unit
    }
    if(showHelp&&current())PanicHomeAlertDialog(onDismissRequest={showHelp=false},title={Text("Calculator help")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("Percent always divides by 100: 10% = 0.1; 200 + 10% = 200.1. For a 10% increase use 200 × (1 + 10%).")
        Text("Parentheses first; factorial/percent, powers, sign, multiply/divide, then add/subtract. Powers associate right: 2^3^2 = 512; −2^2 = −4. Use explicit multiplication.")
        Text("Decimal arithmetic uses 34 significant digits; division rounds half-even. Scientific trig/log/constants and fractional powers are approximate. DEG and RAD select angle units. E enters a number exponent, for example 1E6.")
        Text("Expressions: 512 characters, 256 tokens and 32 nested levels. Powers: −1000 to 1000. Factorial: whole numbers 0 to 1000. Magnitudes: 10⁻¹⁰⁰⁰⁰ to 10¹⁰⁰⁰⁰. Trig angles up to 10⁹ radians. Undefined or out-of-range operations show an error.")
        Text("MS stores, MR recalls, M+ adds, M− subtracts and MC clears memory. Copy places only the result on the system clipboard. Panic does not erase a value already copied there. History keeps the latest 50 completed calculations in this Space.")
    }},confirmButton={TextButton(onClick={showHelp=false}){Text("Done")}})
}

private enum class CalculatorPanel { Memory, History }

private val memoryCommands=listOf(
    "MS" to "Store in memory", "MR" to "Recall memory", "M+" to "Add to memory",
    "M−" to "Subtract from memory", "MC" to "Clear memory",
)

@Composable private fun CalculatorPanelDialog(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit){
    PanicHomeDialog(
        onDismissRequest=onClose,
        properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn),
    ){
        Surface(
            Modifier.padding(16.dp).widthIn(max=460.dp).fillMaxWidth().fillMaxHeight(.78f),
            shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface,
        ){
            Column(Modifier.padding(18.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                    TextButton(onClick=onClose){Text("Done")}
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=6.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp),content=content,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CalculatorReadout(
    model:CalculatorData,copied:Boolean,onHome:()->Unit,onHelp:()->Unit,onInput:(String)->Unit,onCopy:()->Unit,
    onScientific:()->Unit,onAngle:()->Unit,onMemory:()->Unit,onHistory:()->Unit,
){
    val palette=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f).padding(vertical=4.dp)){
            Text("Calculator",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=palette.onBackground)
            Text(if(model.scientific)"Scientific mode" else "Standard mode",
                style=MaterialTheme.typography.labelMedium,color=palette.onSurfaceVariant)
        }
        TextButton(onClick=onHelp,contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.heightIn(min=48.dp)){Text("Help")}
        TextButton(onClick=onHome,contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.heightIn(min=48.dp)){Text("Home")}
    }
    Surface(shape=RoundedCornerShape(26.dp),color=palette.surface,modifier=Modifier.fillMaxWidth()){
        Column(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(listOf(palette.surfaceContainerHigh,palette.surface))
            ).padding(start=16.dp,end=16.dp,top=10.dp,bottom=4.dp),
            horizontalAlignment=Alignment.End,
        ){
            val expressionStyle=MaterialTheme.typography.titleLarge.copy(
                color=palette.onSurfaceVariant,textAlign=TextAlign.End,fontFeatureSettings="tnum",
            )
            BasicTextField(
                value=model.expression,onValueChange=onInput,
                modifier=Modifier.fillMaxWidth().testTag("calculator-expression")
                    .semantics{contentDescription="Expression"},
                textStyle=expressionStyle,maxLines=3,cursorBrush=SolidColor(palette.primary),
                decorationBox={inner->
                    Box(Modifier.fillMaxWidth().heightIn(min=48.dp),contentAlignment=Alignment.CenterEnd){
                        if(model.expression.isEmpty())Text("Enter a calculation",style=MaterialTheme.typography.bodyMedium,
                            color=palette.onSurfaceVariant,textAlign=TextAlign.End)
                        Box(Modifier.fillMaxWidth()){inner()}
                    }
                },
            )
            val result=model.result?.let(CalculatorEngine::format)?:"0"
            Text(
                result,Modifier.fillMaxWidth().testTag("calculator-result"),
                style=MaterialTheme.typography.displaySmall.copy(fontFeatureSettings="tnum",
                    fontSize=if(result.length>16)28.sp else 40.sp),
                fontWeight=FontWeight.SemiBold,color=palette.onSurface,textAlign=TextAlign.End,
            )
            model.error?.let{Text(it,color=palette.error,modifier=Modifier.fillMaxWidth().testTag("calculator-error"),
                style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.End)}
            TextButton(enabled=model.result!=null,onClick=onCopy,modifier=Modifier.heightIn(min=48.dp),
                contentPadding=PaddingValues(horizontal=6.dp)){Text(if(copied)"Copied"else "Copy")}
        }
    }
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
        FilterChip(model.scientific,onScientific,label={Text("Scientific")},modifier=Modifier.heightIn(min=48.dp))
        if(model.scientific)TextButton(onClick=onAngle,modifier=Modifier.heightIn(min=48.dp),
            contentPadding=PaddingValues(horizontal=6.dp)){Text(if(model.radians)"RAD"else "DEG")}
        TextButton(onClick=onMemory,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=9.dp)){
            Text("Memory")
            if(model.memory!=null)Text("M",Modifier.padding(start=4.dp).semantics{contentDescription="Memory contains a value"},
                color=palette.secondary,fontWeight=FontWeight.Bold)
        }
        TextButton(onClick=onHistory,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=9.dp)){Text("History")}
    }
}

private val standardRows=listOf(
    listOf("AC","⌫","%","/"),listOf("7","8","9","*"),listOf("4","5","6","-"),
    listOf("1","2","3","+"),listOf("±","0",".","="),
)
private val parentheses=listOf("(",")")
private val scientificRows=listOf(listOf("sin(","cos(","tan("),listOf("log(","ln(","sqrt("),listOf("^","!","pi"),listOf("e","E"))

@Composable private fun CalculatorStandardKeys(
    onKey:(String)->Unit,onHold:()->Unit,height:Dp,sideParentheses:Boolean,modifier:Modifier=Modifier,
){
    if(sideParentheses){
        Row(modifier,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            CalculatorKeys(parentheses.map{listOf(it)},onKey,onHold,height,Modifier.width(50.dp))
            CalculatorKeys(standardRows,onKey,onHold,height,Modifier.weight(1f))
        }
    }else CalculatorKeys(listOf(parentheses)+standardRows,onKey,onHold,height,modifier)
}

@Composable private fun CalculatorScienceKeys(onKey:(String)->Unit,height:Dp,modifier:Modifier=Modifier){
    Surface(
        modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),
        color=MaterialTheme.colorScheme.surfaceContainer,
    ){
        CalculatorKeys(scientificRows,onKey,{},height,Modifier.padding(6.dp),scientific=true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun CalculatorKeys(
    rows:List<List<String>>,onKey:(String)->Unit,onHold:()->Unit,height:Dp,modifier:Modifier=Modifier,
    scientific:Boolean=false,
){
    val palette=MaterialTheme.colorScheme
    val gap=if(height==48.dp)4.dp else 6.dp
    Column(modifier,verticalArrangement=Arrangement.spacedBy(gap)){
        rows.forEach{row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)){
                row.forEach{label->
                    val description=when(label){
                        "AC"->"Clear all";"⌫"->"Delete last character";"±"->"Change sign";"*"->"Multiply"
                        "/"->"Divide";"-"->"Subtract";"+"->"Add";"="->"Equals";"%"->"Percent"
                        "("->"Open parenthesis";")"->"Close parenthesis";"^"->"Power";"!"->"Factorial"
                        "sqrt("->"Square root";"sin("->"Sine";"cos("->"Cosine";"tan("->"Tangent"
                        "log("->"Base ten logarithm";"ln("->"Natural logarithm";"pi"->"Pi";"e"->"Euler constant"
                        "E"->"Number exponent";"."->"Decimal point";else->label
                    }
                    val glyph=when(label){
                        "*"->"×";"/"->"÷";"-"->"−";"pi"->"π";"sqrt("->"√"
                        "(",")"->label
                        else->label.removeSuffix("(")
                    }
                    val operation=label in listOf("/","*","-","+")
                    val utility=label in listOf("AC","⌫","%","(",")")
                    val color=when{
                        label=="="->palette.primary
                        operation->palette.primary.copy(alpha=.22f).compositeOver(palette.surface)
                        utility||scientific->palette.surfaceContainerHighest
                        else->palette.surfaceContainerHigh
                    }
                    val ink=when{
                        label=="="->palette.onPrimary
                        operation->palette.primary
                        scientific->palette.secondary
                        utility->palette.onSurfaceVariant
                        else->palette.onSurface
                    }
                    Surface(
                        modifier=Modifier.weight(1f).heightIn(min=height).testTag("calculator-key-$label")
                            .semantics{
                                contentDescription=description
                                // Capture this composition's authority so retained actions expire.
                                onClick {onKey(label);true}
                                if(label=="=")onLongClick("Hold equals"){onHold();true}
                            }
                            .combinedClickable(role=Role.Button,onClick={onKey(label)},
                                onLongClick=if(label=="=")onHold else null,onLongClickLabel=if(label=="=")"Hold equals"else null),
                        shape=RoundedCornerShape(percent=48),color=color,contentColor=ink,
                    ){
                        Box(Modifier.padding(horizontal=3.dp,vertical=5.dp),contentAlignment=Alignment.Center){
                            Text(glyph,style=if(scientific)MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                fontWeight=if(label=="="||operation)FontWeight.SemiBold else FontWeight.Medium,maxLines=1)
                        }
                    }
                }
            }
        }
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
