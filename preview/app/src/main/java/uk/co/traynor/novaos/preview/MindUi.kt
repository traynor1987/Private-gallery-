package uk.co.traynor.novaos.preview

import android.os.SystemClock
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable internal fun MindScreen(data:MindData,profile:String,audio:MindAudio,initial:String,launchToken:Int,onHome:()->Unit,onSanctuary:()->Unit){
 var page by rememberSaveable{mutableStateOf(initial)}
 var appliedToken by rememberSaveable{mutableIntStateOf(-1)}
 LaunchedEffect(launchToken){if(appliedToken!=launchToken){page=initial;appliedToken=launchToken}}
 Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("nova-mind")){
  PreviewHeader(if(page=="home")"Nova Mind"else when(page){"breathing"->"Breathe";"grounding"->"Find your ground";"sounds"->"Calm sounds";"mood"->"How are you?";else->"Private journal"},"Your space. Your pace.",onBack={if(page=="home")onHome()else page="home"})
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   when(page){
    "home"->{
     Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.secondaryContainer){Column(Modifier.fillMaxWidth().padding(24.dp)){
      Text("A little room to breathe.",style=MaterialTheme.typography.headlineMedium)
      Text("Nothing to finish. Nothing to prove.",Modifier.padding(vertical=12.dp))
      Button(onClick={page=data.preferred},modifier=Modifier.fillMaxWidth().testTag("calm-now")){Icon(Icons.Default.Spa,null);Text("Calm Now",Modifier.padding(start=10.dp))}
     }}
     listOf("breathing" to "Guided breathing","grounding" to "Grounding","sounds" to "Calm sounds","mood" to "Optional mood check-in","journal" to "Private journal").forEach{(id,title)->OutlinedButton(onClick={page=id},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("mind-$id")){Text(title)}}
     OutlinedButton(onClick=onSanctuary,modifier=Modifier.fillMaxWidth()){Text("Enter Nova Sanctuary")}
     Text("Calm Now opens",style=MaterialTheme.typography.labelLarge)
     Row{FilterChip(data.preferred=="breathing",{data.prefer("breathing")},label={Text("Breathing")});Spacer(Modifier.width(12.dp));FilterChip(data.preferred=="grounding",{data.prefer("grounding")},label={Text("Grounding")})}
     Text("Preview notes stay on this device in ordinary, non-encrypted app storage. These tools offer a quiet space, not medical care.",style=MaterialTheme.typography.bodySmall)
    }
    "breathing"->BreathingExercise()
    "grounding"->GroundingExercise()
    "sounds"->CalmSounds(audio)
    "mood"->MindEntries(data,profile,false)
    "journal"->MindEntries(data,profile,true)
   }
   data.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   Spacer(Modifier.height(24.dp))
  }
 }
}

@Composable internal fun BreathingExercise(){
 var inhale by rememberSaveable{mutableIntStateOf(4)};var exhale by rememberSaveable{mutableIntStateOf(6)}
 var running by rememberSaveable{mutableStateOf(true)}
 var elapsed by rememberSaveable{mutableLongStateOf(0)}
 var reduced by rememberSaveable{mutableStateOf(false)};var vibration by rememberSaveable{mutableStateOf(false)};var sound by rememberSaveable{mutableStateOf(false)}
 val haptic=LocalHapticFeedback.current
 val tone=remember{ToneGenerator(AudioManager.STREAM_MUSIC,15)}
 val owner=LocalContext.current as androidx.lifecycle.LifecycleOwner
 DisposableEffect(owner){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)running=false};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);tone.release()}}
 LaunchedEffect(running,inhale,exhale){var last=SystemClock.elapsedRealtime();while(running){delay(32);val now=SystemClock.elapsedRealtime();elapsed+=now-last;last=now}}
 val isInhale=MindRules.inhale(elapsed,inhale,exhale)
 LaunchedEffect(isInhale,running){if(running){if(vibration)haptic.performHapticFeedback(HapticFeedbackType.LongPress);if(sound)tone.startTone(ToneGenerator.TONE_PROP_BEEP,100)}}
 Text("Let your breathing stay comfortable. You can pause, stop or leave whenever you like.",style=MaterialTheme.typography.bodyLarge)
 Box(Modifier.fillMaxWidth().height(240.dp),contentAlignment=Alignment.Center){
  val scale=if(reduced)1f else .65f+.35f*MindRules.expansion(elapsed,inhale,exhale)
  Surface(Modifier.size((210*scale).dp).testTag("breathing-circle"),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Text(if(!running)"Paused"else if(isInhale)"Breathe in"else "Breathe out",style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)}}
 }
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Button(onClick={running=!running},modifier=Modifier.weight(1f).testTag("breathing-pause")){Text(if(running)"Pause"else "Resume")}
  OutlinedButton(onClick={running=false;elapsed=0},modifier=Modifier.weight(1f).testTag("breathing-stop")){Text("Stop")}
 }
 Text("Inhale · $inhale seconds");Slider(inhale.toFloat(),{inhale=it.toInt();elapsed=0},valueRange=2f..8f,steps=5)
 Text("Exhale · $exhale seconds");Slider(exhale.toFloat(),{exhale=it.toInt();elapsed=0},valueRange=2f..10f,steps=7)
 MindSwitch("Reduced motion",reduced){reduced=it};MindSwitch("Gentle vibration cues",vibration){vibration=it};MindSwitch("Quiet sound cues",sound){sound=it}
}

@Composable private fun MindSwitch(title:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f));Switch(value,onChange)}}

@Composable internal fun GroundingExercise(){
 var step by rememberSaveable{mutableIntStateOf(0)};var simple by rememberSaveable{mutableStateOf(false)};var note by rememberSaveable{mutableStateOf("")}
 val senses=listOf("see","feel","hear","smell","taste")
 Text("You can notice silently, write a few words, or skip anything.")
 FilterChip(simple,{simple=!simple},label={Text(if(simple)"Use 5–4–3–2–1"else "Try without counting")})
 if(simple){
  Text("Feel the surface beneath you.",style=MaterialTheme.typography.headlineMedium)
  Text("Notice one colour nearby. Let your shoulders soften if that feels comfortable. There is no right way to do this.")
 }else if(step<5){
  Text("${MindRules.groundCount(step)} things you can ${senses[step]}",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.testTag("grounding-step"))
  OutlinedTextField(note,{note=it.take(2000)},Modifier.fillMaxWidth(),label={Text("Optional · not saved")},minLines=3)
  Row{Button(onClick={step++;note=""},modifier=Modifier.testTag("grounding-next")){Text("Next")};TextButton(onClick={step++;note=""},modifier=Modifier.testTag("grounding-skip")){Text("Skip this step")}}
 }else {Text("You’re here. Take your time.",style=MaterialTheme.typography.headlineMedium);TextButton(onClick={step=0;note=""}){Text("Start again, if you like")}}
}

@Composable internal fun CalmSounds(audio:MindAudio){
 val names=listOf("rain" to "Rain","ocean" to "Ocean waves","wind" to "Gentle wind","fireplace" to "Fireplace","white" to "White noise","ambient" to "Soft ambient")
 Text("Original generated sound textures · available offline",style=MaterialTheme.typography.bodySmall)
 names.forEach{(id,label)->OutlinedButton(onClick={audio.play(id)},modifier=Modifier.fillMaxWidth()){Icon(if(audio.playing&&audio.sound==id)Icons.Default.GraphicEq else Icons.Default.PlayArrow,null);Text(label,Modifier.padding(start=12.dp))}}
 if(audio.sound.isNotEmpty())Button(onClick={if(audio.playing)audio.pause()else audio.resume()},modifier=Modifier.fillMaxWidth().testTag("calm-sound-toggle")){Text(if(audio.playing)"Pause sound"else "Resume sound")}
 Text("Volume");Slider(audio.volume,{audio.volume(it)},valueRange=0f..1f)
 Text("Sleep timer",style=MaterialTheme.typography.titleMedium)
 Row(Modifier.horizontalScroll(rememberScrollState())){listOf(0,5,15,30,60).forEach{minutes->TextButton(onClick={audio.timer(minutes)}){Text(if(minutes==0)"Off"else "$minutes min")}}}
 Text(if(audio.deadline>0)"Timer active · sound pauses automatically"else "No timer set",style=MaterialTheme.typography.bodySmall)
 audio.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
}

@Composable internal fun MindEntries(data:MindData,profile:String,journal:Boolean){
 var query by rememberSaveable{mutableStateOf("")};var editing by rememberSaveable{mutableStateOf(false)};var id by rememberSaveable{mutableStateOf<String?>(null)}
 var text by rememberSaveable{mutableStateOf("")};var mood by rememberSaveable{mutableStateOf("")};var deleting by remember{mutableStateOf<String?>(null)}
 val moods=listOf("Calm","Okay","Anxious","Overwhelmed","Sad","Frustrated")
 Text("Preview storage · local, not production-encrypted",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
 if(editing){
  Text(if(journal)"A space for your thoughts"else "No explanation needed",style=MaterialTheme.typography.headlineSmall)
  Row(Modifier.horizontalScroll(rememberScrollState())){moods.forEach{m->FilterChip(mood==m,{mood=if(mood==m)""else m},label={Text(m)})}}
  OutlinedTextField(text,{text=it.take(20000)},Modifier.fillMaxWidth().testTag("mind-entry-text"),label={Text(if(journal)"Your entry"else "Optional notes")},minLines=5)
  Row{Button(enabled=text.isNotBlank()||mood.isNotBlank(),onClick={if(data.put(profile,text,mood,journal,id)){editing=false;text="";mood="";id=null}},modifier=Modifier.testTag("mind-entry-save")){Text("Save locally")};TextButton(onClick={editing=false;text="";mood="";id=null}){Text("Cancel")}}
 }else{
  Button(onClick={id=null;text="";mood="";editing=true},modifier=Modifier.fillMaxWidth().testTag("mind-entry-new")){Text(if(journal)"New entry"else "Check in, if you like")}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search your entries")},singleLine=true)
  val entries=data.entries(profile).filter{it.journal==journal&&(it.text.contains(query,true)||it.mood.contains(query,true))}
  if(entries.isEmpty())Text("A quiet, blank page. Nothing is required.",Modifier.padding(vertical=20.dp))
  entries.forEach{entry->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){
   Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(entry.time)),style=MaterialTheme.typography.labelSmall)
   if(entry.mood.isNotBlank())Text(entry.mood,style=MaterialTheme.typography.titleSmall)
   if(entry.text.isNotBlank())Text(entry.text,Modifier.padding(top=8.dp))
   Row{TextButton(onClick={id=entry.id;text=entry.text;mood=entry.mood;editing=true}){Text("Edit")};TextButton(onClick={deleting=entry.id}){Text("Delete")}}
  }}}
 }
 deleting?.let{entryId->AlertDialog(onDismissRequest={deleting=null},title={Text("Delete this entry?")},text={Text("This removes the local preview entry permanently.")},confirmButton={TextButton(onClick={if(data.delete(profile,entryId))deleting=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Keep")}})}
}

@Composable internal fun SanctuaryScreen(data:PreviewData,audio:MindAudio,chat:ChatLiteData,onMind:()->Unit,onCalm:()->Unit,onLaunch:(String)->Unit,onExit:()->Unit){
 Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF173D43),Color(0xFF111D32)))).safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()).testTag("nova-sanctuary"),verticalArrangement=Arrangement.spacedBy(18.dp)){
  Text("Nova Sanctuary",style=MaterialTheme.typography.headlineLarge,color=Color.White)
  Text("You have room here.",style=MaterialTheme.typography.titleLarge,color=Color(0xFFBFDFD6))
  Text("A quieter Home, whenever you need it. Android calls and notifications continue normally.",color=Color.White)
  Button(onClick=onCalm,modifier=Modifier.fillMaxWidth()){Text("Calm Now")}
  OutlinedButton(onClick=onMind,modifier=Modifier.fillMaxWidth()){Text("Open Nova Mind",color=Color.White)}
  TextButton(onClick={if(audio.playing)audio.pause()else audio.play("rain")}){Text(if(audio.playing)"Pause calming sound"else "Play gentle rain",color=Color.White)}
  OutlinedButton(onClick={onLaunch("chat")},modifier=Modifier.fillMaxWidth().testTag("sanctuary-friends")){Text("Friends Mode",color=Color.White)}
  Row{Text("Enter Friends Mode with Sanctuary",Modifier.weight(1f),color=Color.White);Switch(chat.sanctuary(data.state.current),{chat.sanctuary(data.state.current,it)})}
  Text("Your relaxing favourites",color=Color.White)
  val favourites=data.shell.favourites(data.state.current).mapNotNull{data.app(it)}.filter{it.website!=null}
  if(favourites.isEmpty())Text("Choose your relaxing apps in Edge favourites. Your usual Home stays as you left it.",color=Color(0xFFBFDFD6))
  favourites.forEach{app->OutlinedButton(onClick={onLaunch(app.id)},modifier=Modifier.fillMaxWidth()){AppIcon(app,32.dp);Text(app.name,Modifier.padding(start=12.dp),color=Color.White)}}
  Spacer(Modifier.height(24.dp))
  Button(onClick=onExit,modifier=Modifier.fillMaxWidth().testTag("exit-sanctuary")){Text("Exit Sanctuary")}
 }
}
