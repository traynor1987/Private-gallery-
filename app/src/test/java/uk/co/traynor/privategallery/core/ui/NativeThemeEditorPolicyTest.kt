package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.*
import org.junit.Test

class NativeThemeEditorPolicyTest {
 @Test fun explicit_native_choices_seed_primary_only() {
  for(theme in listOf("dark","light","system")) {
   assertEquals(theme,NativeThemePolicy.initial(theme,"dark",false))
   assertEquals("light",NativeThemePolicy.initial(theme,"light",true))
  }
  assertEquals("dark",NativeThemePolicy.initial(null,"dark",false))
  assertEquals("system",NativeThemePolicy.initial("bad","bad",true))
 }
 @Test fun theme_controls_include_all_three_choices() {
  assertEquals("light",NativeThemePolicy.next("dark"))
  assertEquals("system",NativeThemePolicy.next("light"))
  assertEquals("dark",NativeThemePolicy.next("system"))
 }
 @Test fun media_dialogs_are_excluded_from_browser_home_and_other_features() {
  for(destination in listOf("GALLERY","VAULT","FAVOURITE")) {
   assertTrue(EditingContextPolicy.allowed(destination,false,false))
   assertFalse(EditingContextPolicy.allowed(destination,true,false))
   assertFalse(EditingContextPolicy.allowed(destination,false,true))
  }
  for(destination in listOf("BROWSER","SETTINGS","LOCK","HOME","WEBAPP"))
   assertFalse(EditingContextPolicy.allowed(destination,false,false))
 }
}
