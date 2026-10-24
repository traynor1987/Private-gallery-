import os,pathlib,re,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release1751ConfigurationTest(unittest.TestCase):
 def test_exact_installed38_to39_original_source(self):
  result=subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s" "$release_version" "$release_code" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.5.1-privacy-viewer-repair'},capture_output=True,text=True)
  self.assertEqual(0,result.returncode,result.stderr)
  self.assertEqual('1.7.5.1 39 38 69bfade789df4a49d911091bb8cf8205c88a3a39',result.stdout)
 def test_android16_minimum(self):
  build=(ROOT/'app/build.gradle.kts').read_text()
  self.assertEqual(['36'],re.findall(r'\bminSdk\s*=\s*(\d+)',build),'Owner-approved Android16/API36 minimum must be enforced by the installer')
 def test_recents_exclusion_survives_screenshot_preference(self):
  main=(ROOT/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
  policy=main.split('private fun applyScreenPrivacy(',1)[1].split('private fun checkForUpdates',1)[0]
  self.assertIn('setRecentsScreenshotEnabled(false)',policy)
  self.assertNotRegex(main,r'setRecentsScreenshotEnabled\(\s*true\s*\)')
  # On supported platforms this call is outside the foreground allowed/denied branch.
  self.assertRegex(policy,r'else\s*\{[\s\S]*?privacyWindows.protect\(\)[\s\S]*?\}\s*if \(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU\)\s*\{\s*setRecentsScreenshotEnabled\(false\)')
 def test_supported_focused_matrix_keeps_all_repair_classes(self):
  workflow=(ROOT/'.github/workflows/refinement1751.yml').read_text()
  self.assertIn("api-level: 36",workflow)
  self.assertIn("api-level: '37.0'",workflow)
  self.assertNotIn('api32',workflow)
  self.assertNotIn('Pre33Recents1751Test',workflow)
  for stage in ('api36','api37'):
   self.assertIn("test '${{ steps."+stage+".outcome }}' = success",workflow)
   command=next(line for line in workflow.splitlines() if 'bash scripts/run_repair1751_focused.sh '+stage+' ' in line)
   self.assertEqual({'PrivacyViewerRepair1751Test','PanicCapture1751Test','PrivacyShield175Test','MediaExperience175Test','PrivateVideoPlayerTest','SmartUnlockLifecycle173Test','ProfessionalUiTest'},{name.rsplit('.',1)[1] for name in command.split()[-1].split(',')})
 def test_candidate_floor_is_part_of_original_key_and_upgrade_gates(self):
  for name in ('verify_production_candidate.sh','verify_synthetic_upgrade.sh'):
   self.assertIn("sdkVersion:'$release_min_sdk'",(ROOT/'scripts'/name).read_text())
  result=subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s" "$release_min_sdk"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.5.1-privacy-viewer-repair'},capture_output=True,text=True)
  self.assertEqual('36',result.stdout)
 def test_android17_instrumentation_uses_fixed_stable_cohort(self):
  build=(ROOT/'app/build.gradle.kts').read_text()
  for dependency in ('androidx.test.espresso:espresso-core:3.7.0','androidx.test:runner:1.7.0','androidx.test.ext:junit:1.3.0'):
   self.assertIn('androidTestImplementation("'+dependency+'")',build)
   self.assertNotIn('  implementation("'+dependency+'")',build)
if __name__=='__main__':unittest.main()
