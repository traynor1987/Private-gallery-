import copy
import unittest
from unittest.mock import patch

import novaos_build99_production as gate


SOURCE = "a" * 40
CANDIDATE = "b" * 40
REHEARSAL = "c" * 40
TREE_RELEASE = "d" * 40
TREE_CANDIDATE = "e" * 40
INPUT_HASHES = {"signing-control": "1" * 64}


def manifest():
    return {
        "schema": 1, "mode": "production", "main_parent": SOURCE, "candidate": CANDIDATE,
        "candidate_tree": TREE_CANDIDATE, "source_tree": TREE_RELEASE,
        "version": "2.0.0", "version_code": 99, "development": {}, "full_security": {},
        "inputs_sha256": INPUT_HASHES,
        "rehearsal": {
            "run": 101, "attempt": 1, "artifact": 201, "sha256": "2" * 64,
            "approval_commit": REHEARSAL,
        },
    }


def pull_request(source=SOURCE):
    repo = {"full_name": gate.REPO}
    return {
        "number": 87, "state": "closed", "merged": True, "merge_commit_sha": source,
        "head": {"sha": CANDIDATE, "ref": gate.BRANCH, "repo": repo},
        "base": {"ref": "main", "repo": repo},
    }


class Build99Production(unittest.TestCase):
    def test_manifest_pins_exact_current_main_and_controls(self):
        approved = manifest()
        with patch.object(gate, "checksums", return_value=INPUT_HASHES):
            gate.verify_manifest(approved, SOURCE)
            for key, incorrect in (
                ("source_tree", "unknown"), ("main_parent", "f" * 40),
                ("version_code", 98), ("inputs_sha256", {}),
            ):
                invalid = copy.deepcopy(approved)
                invalid[key] = incorrect
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, "Signing manifest"):
                    gate.verify_manifest(invalid, SOURCE)
            approved["rehearsal"]["attempt"] = 2
            with self.assertRaisesRegex(ValueError, "specific owner rehearsal"):
                gate.verify_manifest(approved, SOURCE)

    def test_allows_exact_merge_and_only_the_marker_path(self):
        git_results = [f"{SOURCE} {REHEARSAL} {CANDIDATE}", TREE_RELEASE, TREE_CANDIDATE]
        with patch.object(gate, "api", return_value=pull_request()), \
             patch.object(gate, "verify_candidate_configuration") as identity:
            with patch.object(gate, "git", side_effect=[*git_results, "A\t" + gate.REHEARSAL_MARKER]):
                gate.verify_merge(manifest(), SOURCE)
                identity.assert_called_once_with(CANDIDATE, TREE_CANDIDATE)
            for diff in ("", "A\t" + gate.REHEARSAL_MARKER + "\nM\tapp/build.gradle.kts", "M\t" + gate.REHEARSAL_MARKER):
                with self.subTest(diff=diff), patch.object(gate, "git", side_effect=[*git_results, diff]):
                    with self.assertRaisesRegex(ValueError, "Merged tree differs"):
                        gate.verify_merge(manifest(), SOURCE)

    def test_rejects_another_production_merge(self):
        with patch.object(gate, "api", return_value=pull_request("f" * 40)):
            with self.assertRaisesRegex(ValueError, "reviewed merge of Wave A"):
                gate.verify_merge(manifest(), SOURCE)


if __name__ == "__main__":
    unittest.main()
