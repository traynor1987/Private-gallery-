package uk.co.traynor.privategallery.home2
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.ReleaseScreenshots62
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
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
  val seeded=Game2048(intArrayOf(0,2,2,0, 0,0,0,0, 0,0,0,0, 0,0,0,0),0,2026,null)
  ArcadeData(space).save(seeded)
  compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides context!!){IntegratedHome(access!!,true,{}, {}, {},null,null)}}
  compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("drawer-search").performTextInput("Nova Arcade");compose.onNodeWithTag("app-arcade").performClick()
  compose.onNodeWithTag("nova-arcade").assertIsDisplayed();compose.onNodeWithTag("shortcut-2048").performScrollTo().performClick()
  compose.onNodeWithTag("arcade-achievements").performClick()
  compose.onNodeWithTag("arcade-achievement-progress").assertIsDisplayed()
  compose.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("nova-arcade"))).performClick()
  compose.onNodeWithTag("arcade-settings").performClick()
  compose.onNodeWithContentDescription("Snake speed").assertIsDisplayed()
  compose.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("nova-arcade"))).performClick()
  compose.runOnIdle{assertTrue(PreviewData(space,null).profile.pages.any{"arcade-2048" in it})}
  compose.onNodeWithTag("continue-2048").performScrollTo().performClick()
  compose.onNodeWithTag("tiles-board").assertIsDisplayed().performTouchInput { swipeLeft() }
  compose.runOnIdle {
   val moved=checkNotNull(ArcadeData(space).tiles)
   assertEquals(4L,moved.score)
   assertTrue(moved.canUndo())
   assertEquals(seeded.move(0).encode(),moved.encode())
  }
  compose.onNodeWithTag("arcade-directions").performScrollTo().performClick()
  compose.onNodeWithContentDescription("Move left").performScrollTo().performClick()
  compose.runOnIdle{assertNotNull(ArcadeData(space).tiles)}
  compose.onNodeWithTag("nova-home").performClick();compose.onNodeWithTag("app-arcade-2048").performClick();compose.onNodeWithTag("tiles-board").assertIsDisplayed()
  space.close()
 }
 @Test fun coverInnerLandscapeKeepBoardsAndTouchControlsReachable(){
  val space=fixture();var width by mutableIntStateOf(360);var height by mutableIntStateOf(720);var game by mutableStateOf<String?>(null)
  val home=PreviewData(space,null)
  compose.setContent{
   if(attached)CompositionLocalProvider(LocalContext provides space){
    PrivateGalleryTheme(AppTheme.DARK){
     Column(Modifier.requiredSize(width.dp,height.dp).testTag("arcade-review-viewport").navigationBarsPadding()){
      Box(Modifier.weight(1f).fillMaxWidth()){key(game){ArcadeScreen(home,game,onHome={})}}
      NovaNavigation(home.shell,onRecents={},onHome={},onBack={})
     }
    }
   }
  }
  for (size in listOf(360,840)) {
   compose.runOnIdle{width=size}
   compose.onNodeWithTag("play-2048").assertIsDisplayed()
   val snake=compose.onNodeWithTag("play-snake").assertIsDisplayed().getUnclippedBoundsInRoot()
   val nav=compose.onNodeWithTag("nova-home").getUnclippedBoundsInRoot()
   assertTrue("All three game actions must fit above Nova navigation",snake.bottom<=nav.top)
   ReleaseScreenshots62.capture(compose.onNodeWithTag("arcade-review-viewport"),"arcade-hub-$size")
  }
  for(id in listOf("2048","sudoku","snake"))for(size in listOf(360 to 720,840 to 720,840 to 360)){
   compose.runOnIdle{game=id;width=size.first;height=size.second}
   val board=compose.onNodeWithTag(if(id=="2048")"tiles-board"else "$id-board")
   if(size.second==720){
    board.assertIsDisplayed()
    val bounds=board.getUnclippedBoundsInRoot()
    assertEquals("Square $id board at ${size.first}dp",(bounds.right-bounds.left).value,(bounds.bottom-bounds.top).value,1f)
    val essential=compose.onNodeWithTag(when(id){"2048"->"arcade-directions";"sudoku"->"sudoku-number-9";else->"snake-pause"})
    essential.assertIsDisplayed()
    val nav=compose.onNodeWithTag("nova-home").getUnclippedBoundsInRoot()
    assertTrue("$id controls must fit above Nova navigation",essential.getUnclippedBoundsInRoot().bottom<=nav.top)
    ReleaseScreenshots62.capture(compose.onNodeWithTag("arcade-review-viewport"),"arcade-$id-${size.first}")
   }
   if(size.first<600)board.performScrollTo()
   board.assertIsDisplayed()
   when(id){
    "2048"->compose.onNodeWithTag("arcade-directions").performScrollTo().assertIsDisplayed()
    "sudoku"->{
     compose.onNodeWithTag("sudoku-number-1").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
     compose.onNodeWithTag("sudoku-number-9").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
     compose.onNodeWithTag("sudoku-pause").performScrollTo().assertIsDisplayed()
    }
    else->compose.onNodeWithTag("snake-pause").performScrollTo().assertIsDisplayed()
   }
   if(size.first>=600)board.assertIsDisplayed()
  };space.close()
 }
 @Test fun snakeLifecyclePauseDiscardsQueuedInputAndResumeIsExplicit(){
  val space=fixture();compose.setContent{if(attached)CompositionLocalProvider(LocalContext provides space){ArcadeScreen(PreviewData(space,null),"snake",onHome={})}}
  compose.onNodeWithTag("arcade-directions").performScrollTo().performClick()
  compose.onNodeWithTag("snake-pause").performScrollTo().performClick()
  compose.onNodeWithContentDescription("Move down").performScrollTo().performClick();compose.onNodeWithContentDescription("Move left").performScrollTo().performClick()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  val saved=SnakeGame.decode(space.getSharedPreferences(ArcadePreferences.NAME,0).getString("save:snake",null)!!);assertTrue(saved.paused);assertArrayEquals(intArrayOf(),saved.queue())
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  compose.onNodeWithTag("snake-pause").performScrollTo().assertTextContains("Start / Resume")
  assertEquals(saved.encode(),SnakeGame.decode(space.getSharedPreferences(ArcadePreferences.NAME,0).getString("save:snake",null)!!).encode());space.close()
 }
}
