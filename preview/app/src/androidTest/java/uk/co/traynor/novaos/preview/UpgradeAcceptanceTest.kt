package uk.co.traynor.novaos.preview
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class UpgradeAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun code6ConfigurationAndWidgetsSurviveInPlaceUpgrade(){
  val d=rule.activity.previewData
  val w=rule.activity.widgetData;val slot=w.slots("primary").single()
  assertEquals("upgrade-widget",slot.id);assertEquals(1,slot.active);assertEquals(listOf("media","note"),slot.items.map{it.type});assertEquals("Synthetic retained widget note",slot.items[1].config);assertTrue(w.reduced)
  assertTrue(rule.activity.mindData.entries("primary").any{it.id=="upgrade-mind-fixture"&&it.text=="Synthetic upgrade journal"})
  assertEquals("light",d.state.theme);assertEquals(5,d.profile.columns);assertEquals(64,d.profile.iconSize)
  assertEquals("Dusk",d.profile.wallpaper.builtin);assertEquals(1.4f,d.profile.wallpaper.zoom);assertEquals(.25f,d.profile.wallpaper.dim)
  assertEquals(listOf("folder:upgrade","store"),d.profile.pages[0]);assertEquals("Upgrade keep",d.profile.folders.single().name)
  assertEquals("Photopea",d.profile.installed.single().name);assertEquals(d.profile.installed.single().id,d.profile.dock[0]);assertEquals(d.profile,d.state.hidden)
 }
}
