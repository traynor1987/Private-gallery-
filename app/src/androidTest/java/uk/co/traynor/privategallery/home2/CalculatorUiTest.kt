package uk.co.traynor.privategallery.home2
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.SpaceContext

class CalculatorUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private var root:File?=null;private var access:EvidenceAccess?=null;private var local:Context?=null
 private var attached by mutableStateOf(true)
 private fun fixture():SpaceContext{
  val base=compose.activity;val dir=File(base.cacheDir,"calculator-ui-${UUID.randomUUID()}");root=dir
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(dir,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(dir,"hidden").apply{mkdirs()};override fun getApplicationContext():Context=base.applicationContext};local=context
  val identity=Any();access=EvidenceAccess.primary(context,identity,{identity},{true},{ByteArray(32){39}},Any());return SpaceContext(context,access!!)
 }
 @After fun close(){compose.runOnIdle{attached=false};access?.close();root?.deleteRecursively()}
 @Test fun drawerLaunchArithmeticMemoryHistoryAndCopyUseNativeCalculator(){
  val space=fixture();compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides local!!){IntegratedHome(access!!,true,{},{},{},null,null)}}
  compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("drawer-search").performTextInput("Nova Calculator");compose.onNodeWithTag("app-nova-calculator").performClick()
  compose.onNodeWithTag("calculator").assertIsDisplayed();compose.onNodeWithTag("calculator-expression").performTextInput("0.1+0.2")
  compose.onNodeWithTag("calculator-key-=").performScrollTo().performClick();compose.onNodeWithTag("calculator-result").performScrollTo().assertTextEquals("0.3")
  compose.onNodeWithContentDescription("Store in memory").performScrollTo().performClick();compose.onNodeWithText("Copy").performScrollTo().performClick()
  compose.runOnIdle{assertEquals("0.3",CalculatorData(space).memory!!.toPlainString());assertEquals("0.3",(compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip!!.getItemAt(0).text.toString())}
  compose.onNodeWithText("History").performScrollTo().performClick();compose.onNodeWithTag("calculator-history-0").performScrollTo().assertExists()
  compose.onNodeWithContentDescription("Save calculation history").performScrollTo().performClick();compose.runOnIdle{assertTrue(CalculatorData(space).history.isEmpty())};space.close()
 }
 @Test fun foldSizesLargeTextScientificAndStandardControlsRemainReachable(){
  val space=fixture();var width by mutableIntStateOf(360);var height by mutableIntStateOf(720)
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides space){val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){Box(Modifier.requiredSize(width.dp,height.dp)){CalculatorScreen(onHome={})}}}}
  compose.onNodeWithText("Scientific").performScrollTo().performClick()
  for(size in listOf(360 to 720,840 to 720,840 to 360)){
   compose.runOnIdle{width=size.first;height=size.second}
   for(description in listOf("Sine","Square root","Factorial","Equals","Change sign","Delete last character"))compose.onNodeWithContentDescription(description).performScrollTo().assertIsDisplayed()
  };space.close()
 }
 @Test fun longPressSequenceHasNeutralSemanticsNeverEvaluatesAndStaleCallbacksCannotAct(){
  val space=fixture();var active by mutableStateOf(true)
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides space){CalculatorScreen(active,onHome={})}}
  compose.onNodeWithTag("calculator-expression").performTextInput("6+7")
  val equals=compose.onNodeWithTag("calculator-key-=").performScrollTo()
  val hold=checkNotNull(equals.fetchSemanticsNode().config[SemanticsActions.OnLongClick].action)
  assertEquals("Hold equals",equals.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
  compose.runOnIdle{repeat(5){hold()};assertTrue(CalculatorData(space).history.isEmpty())}
  compose.onAllNodes(hasText("Hidden",substring=true)).assertCountEquals(0)
  val click=checkNotNull(equals.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.runOnIdle{click();repeat(5){hold()};assertTrue(CalculatorData(space).history.isEmpty())}
  compose.onNodeWithTag("calculator-expression").performTextInput("3+4")
  val newClick=checkNotNull(equals.performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action)
  compose.runOnIdle{active=false};compose.waitForIdle();compose.runOnIdle{active=true};compose.waitForIdle()
  compose.runOnIdle{newClick();assertTrue(CalculatorData(space).history.isEmpty())}
  compose.onNodeWithTag("calculator-key-=").performScrollTo().performClick();compose.runOnIdle{assertEquals("7",CalculatorData(space).history.single().result())};space.close()
 }
}
