package uk.co.traynor.privategallery.home2
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.arcade.*

class ArcadeUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private var root:File?=null;private var access:EvidenceAccess?=null;private var context:Context?=null
 private var attached by mutableStateOf(true)
 private fun fixture():SpaceContext {
  val base=compose.activity;val dir=File(base.cacheDir,"arcade-ui-${UUID.randomUUID()}");root=dir
  val local=object:ContextWrapper(base){override fun getFilesDir()=File(dir,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(dir,"hidden").apply{mkdirs()};override fun getApplicationContext():Context=base.applicationContext};context=local
  val identity=Any();val owner=EvidenceAccess.primary(local,identity,{identity},{true},{ByteArray(32){39}},Any());access=owner
  return SpaceContext(local,owner)
 }
 @After fun close(){compose.runOnIdle{attached=false};access?.close();root?.deleteRecursively()}
 @Test fun catalogueShortcutLaunchesNativeGameAndAddsToExistingHomeLayout(){
  val space=fixture()
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides context!!){IntegratedHome(access!!,true,{}, {}, {},null,null)}}
  compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("drawer-search").performTextInput("Nova Arcade");compose.onNodeWithTag("app-arcade").performClick()
  compose.onNodeWithTag("nova-arcade").assertIsDisplayed();compose.onNodeWithTag("shortcut-2048").performScrollTo().performClick()
  compose.runOnIdle{assertTrue(PreviewData(space,null).profile.pages.any{"arcade-2048" in it})}
  compose.onNodeWithTag("play-2048").performScrollTo().performClick();compose.onNodeWithTag("tiles-board").assertIsDisplayed()
  compose.onNodeWithContentDescription("Move left").performScrollTo().performClick()
  compose.runOnIdle{assertNotNull(ArcadeData(space).tiles)}
  compose.onNodeWithTag("nova-home").performClick();compose.onNodeWithTag("app-arcade-2048").performClick();compose.onNodeWithTag("tiles-board").assertIsDisplayed()
  space.close()
 }
 @Test fun coverInnerLandscapeKeepBoardsAndTouchControlsReachable(){
  val space=fixture();var width by mutableIntStateOf(360);var height by mutableIntStateOf(720);var game by mutableStateOf("2048")
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides space){Box(Modifier.requiredSize(width.dp,height.dp)){key(game){ArcadeScreen(PreviewData(space,null),game,onHome={})}}}}
  for(id in listOf("2048","sudoku","snake"))for(size in listOf(360 to 720,840 to 720,840 to 360)){
   compose.runOnIdle{game=id;width=size.first;height=size.second}
   compose.onNodeWithTag(if(id=="2048")"tiles-board"else "$id-board").performScrollTo().assertIsDisplayed()
   when(id){"2048"->compose.onNodeWithContentDescription("Move left").performScrollTo().assertIsDisplayed();"sudoku"->compose.onNodeWithTag("sudoku-number-1").performScrollTo().assertIsDisplayed();else->compose.onNodeWithTag("snake-pause").performScrollTo().assertIsDisplayed()}
   if(id=="sudoku"&&size.second==720){compose.onNodeWithTag("sudoku-board").performScrollTo();uk.co.traynor.privategallery.ReleaseScreenshots62.capture(compose.onRoot(),"arcade-sudoku-${size.first}")}
  };space.close()
 }
 @Test fun snakeLifecyclePauseDiscardsQueuedInputAndResumeIsExplicit(){
  val space=fixture();compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides space){ArcadeScreen(PreviewData(space,null),"snake",onHome={})}}
  compose.onNodeWithTag("snake-pause").performScrollTo().performClick()
  compose.onNodeWithContentDescription("Move down").performScrollTo().performClick();compose.onNodeWithContentDescription("Move left").performScrollTo().performClick()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  val saved=SnakeGame.decode(space.getSharedPreferences(ArcadePreferences.NAME,0).getString("save:snake",null)!!);assertTrue(saved.paused);assertArrayEquals(intArrayOf(),saved.queue())
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithTag("snake-pause").performScrollTo().assertTextContains("Start / Resume")
  assertEquals(saved.encode(),SnakeGame.decode(space.getSharedPreferences(ArcadePreferences.NAME,0).getString("save:snake",null)!!).encode());space.close()
 }
}
