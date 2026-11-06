from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]
class NavigationSourceContract(unittest.TestCase):
 def test_shared_row_is_used_by_home_and_native_shell(self):
  row=R/'app/src/main/java/uk/co/traynor/privategallery/ui/NovaNavigation.kt'
  self.assertTrue(row.exists(),'Shared NovaNavigationRow is missing')
  self.assertIn('NovaNavigationRow(', (R/'app/src/main/java/uk/co/traynor/privategallery/home2/PreviewShellUi.kt').read_text())
  self.assertIn('NovaNavigationRow(', (R/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text())
 def test_app_mode_has_secure_bottom_sheet_and_no_floating_menu(self):
  source=(R/'app/src/main/java/uk/co/traynor/privategallery/ui/PrivateAppControls.kt').read_text()
  self.assertIn('private-app-menu-sheet',source)
  self.assertIn('SecureFlagPolicy.SecureOn',source)
  self.assertIn('onOverflow=',source)
  self.assertNotIn('PanicHomeDropdownMenu(expanded',source)
  browser=(R/'app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt').read_text()
  self.assertNotIn('if(appStyle && !novaCovered && ui.fullscreen==null && !ui.webVideoView)Box(Modifier.align(Alignment.TopEnd)',browser)
 def test_app_notice_preserves_consent_with_saved_app_presentation(self):
  source=(R/'app/src/main/java/uk/co/traynor/privategallery/ui/BrowserBetaNotice.kt').read_text()
  self.assertTrue('appName:String?' in source,'Notice cannot present the intended saved app')
  self.assertTrue('if(store.accept())accepted=true' in source,'Explicit one-time consent gate changed')
  browser=(R/'app/src/main/java/uk/co/traynor/privategallery/ui/BrowserV2Home.kt').read_text()
  self.assertTrue('appName=noticeApp?.takeIf' in browser,'Saved app identity is not supplied to notice')
if __name__=='__main__':unittest.main()
