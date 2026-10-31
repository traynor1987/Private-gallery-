"""Synthetic pre-signer fixture provenance; this does not execute an Android build."""
import copy
from pathlib import Path
import unittest
from scripts.verify_compiled_upgrade179 import BASELINE, TARGET, BUILD_TARGET, sha, transform, validate

ROOT=Path(__file__).resolve().parents[2]
class Compilation179(unittest.TestCase):
    def test_exact_transformations_and_tampering(self):
        fixture=b'fixture '+TARGET;build=b'build\n'+BUILD_TARGET+b'\n';compiler=b'reviewed compiler';apk=b'instrumentation'
        changed,changed_build=transform(fixture,build)
        proof=dict(schema=1,decision='COMPILED',baseline_source_sha=BASELINE,candidate_source_sha='1'*40,fixture_sha256=sha(fixture),
            transformed_fixture_sha256=sha(changed),compiler_sha256=sha(compiler),baseline_build_sha256=sha(build),transformed_build_sha256=sha(changed_build),
            instrumentation_apk_sha256=sha(apk),instrumentation_apk_bytes=len(apk),instrumentation_package_sha256='7'*64,instrumentation_manifest_sha256='8'*64,transformations=['remove_debug_applicationIdSuffix','fixture_target_production_package'])
        validate(proof,'1'*40,fixture,compiler,apk,build)
        for key in proof:
            bad=copy.deepcopy(proof);bad[key]=None
            with self.subTest(key=key),self.assertRaises((ValueError,TypeError)):validate(bad,'1'*40,fixture,compiler,apk,build)
        for index,bad in [(2,b'tampered '+TARGET),(3,b'other compiler'),(4,b'other apk'),(5,build+b'changed')]:
            args=[proof,'1'*40,fixture,compiler,apk,build];args[index]=bad
            with self.subTest(index=index),self.assertRaises(ValueError):validate(*args)
    def test_missing_or_duplicate_transform_target_rejected(self):
        for fixture,build in [(b'none',BUILD_TARGET),(TARGET*2,BUILD_TARGET),(TARGET,b'none'),(TARGET,BUILD_TARGET*2)]:
            with self.assertRaises(ValueError):transform(fixture,build)
    def test_fixture_is_additive_to_all_legacy_functions(self):
        import re
        old=(ROOT/'docs/release178/UpgradeFixtureTest.kt').read_text();new=(ROOT/'docs/release179/UpgradeFixtureTest.kt').read_text()
        old_functions=set(re.findall(r'fun (\w+)\(',old));new_functions=set(re.findall(r'fun (\w+)\(',new))
        self.assertTrue(old_functions<=new_functions)
        self.assertNotIn('import uk.co.traynor.privategallery.core.evidence',new)
        self.assertIn('SourceReference',new)
        # Preserve the entire legacy body, including every assertion, not just helper names.
        start=new.index('    /** Candidate-only APIs stay reflective:')
        end=new.index('    @Test fun seed()',start)
        stripped=new[:start]+new[end:]
        stripped=stripped.replace('            candidateEvidence(before,false)\n','').replace('        if(restarted)candidateEvidence(before,true)\n','')
        for current,prior in [('code50 -> code51','code49 -> code50'),('baselineCode == 50 && candidateCode == 51','baselineCode == 49 && candidateCode == 50'),('actual code50 source','actual code49 source'),('Original code50 seed','Original code49 seed'),('keeps its code50 ABI','keeps its code49 ABI'),('baseline50 instrumentation','baseline49 instrumentation'),('explicit code51 style','explicit code50 style')]:
            stripped=stripped.replace(current,prior)
        self.assertEqual(old.strip(),stripped.strip().replace('    }\n\n    @Test fun seed()', '    }\n    @Test fun seed()',1))

    def test_reflective_fixture_constructor_arity_matches_actual_candidate_classes(self):
        import re,subprocess,tempfile
        fixture=(ROOT/'docs/release179/UpgradeFixtureTest.kt').read_text()
        constructors=re.findall(r'evidenceRecord\("(\w+)",(\d+),',fixture)
        self.assertTrue(constructors)
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);source=root/'FixtureAbiCheck.java'
            checks='\n'.join('if(java.util.Arrays.stream(Class.forName("uk.co.traynor.privategallery.core.evidence.EvidenceCatalog$'+name+'").getConstructors()).filter(c->c.getParameterCount()=='+arity+').count()!=1)throw new AssertionError("'+name+' arity");' for name,arity in constructors)
            source.write_text('public class FixtureAbiCheck { public static void main(String[] args)throws Exception {'+checks+'} }')
            base=ROOT/'app/src/main/java/uk/co/traynor/privategallery/core/evidence'
            sources=[base/(name+'.java') for name in ['EvidenceCatalog','EvidenceCodec','EvidenceTextHints','EvidenceStrictJson']]
            subprocess.run(['java','com.sun.tools.javac.Main','-d',str(root),*[str(p) for p in sources],str(source)],check=True,capture_output=True,text=True)
            subprocess.run(['java','-cp',str(root),'FixtureAbiCheck'],check=True,capture_output=True,text=True)
