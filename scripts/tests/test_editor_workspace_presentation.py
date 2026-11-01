"""Fast source invariants; Compose runtime assertions live in EditorWorkspacePresentationTest."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
EDITOR = ROOT / 'app/src/main/java/uk/co/traynor/privategallery/ui/PhotoEditor.kt'

class EditorWorkspacePresentationChecks(unittest.TestCase):
    def test_combine_never_replaces_workspace_canvas_with_only_controls(self):
        self.assertNotIn('if (combineSetup) toolPanel()', EDITOR.read_text())

    def test_provider_settings_have_an_explicit_collapsed_disclosure(self):
        source = EDITOR.read_text()
        self.assertIn('mutableStateOf(false) } // Provider settings start collapsed', source)
        self.assertIn('editor-provider-disclosure', source)
        self.assertIn('if (providerSettingsExpanded)', source)

    def test_editor_inherits_theme_and_offers_owner_security_actions(self):
        source = EDITOR.read_text()
        self.assertNotIn('PrivateGalleryTheme(uk.co.traynor.privategallery.core.ui.AppTheme.DARK)', source)
        self.assertIn('val security = novaSecurityActions()', source)
        self.assertIn('securityExit(security.lock)', source)
        self.assertIn('securityExit(security.panic)', source)

    def test_actions_and_source_keep_existing_security_gates(self):
        source = EDITOR.read_text()
        self.assertIn('resolved.processing == AiProcessing.CLOUD && (!sessionConsent', source)
        self.assertIn('candidate !in allowedSources', source)
        self.assertIn('Lifecycle.Event.ON_STOP', source)
        self.assertIn('onSaveAiWithSource', source)

if __name__ == '__main__': unittest.main()
