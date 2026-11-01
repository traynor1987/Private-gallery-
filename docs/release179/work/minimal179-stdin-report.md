# Minimal route-loop stdin correction

Baseline: `ae93b06a2a57cbfec9226b4b78e5532f3f438f68`. Scope: two subprocess launch arguments in `scripts/evidence179_clock.py`, regressions in the existing `scripts/tests/test_evidence179_clock.py`, and this report. Preimages of both files and the unchanged runner test module are preserved with relative paths under `/tmp/minimal179-stdin-before`.

The clock helper inherited the runner's four-row `while read` input. A nested noninteractive command could consume the remaining route selections. `Recorder.command` now passes `stdin=subprocess.DEVNULL` to `subprocess.run`; the original invocation's `Popen` does likewise. No argv, identity, timing, raw evidence, exit-status or replay logic changed. The runner, workflows, Android sources, production code, version, 153-case manifest and six-positive/eight-negative requirements were not edited.

## RED and GREEN

Exact command, run before and after the two-line helper change:

```sh
python3 -m unittest scripts.tests.test_evidence179_clock.RecorderStdinIsolation scripts.tests.test_evidence179_clock.HostProtocolProcess.test_original_child_sees_eof_without_losing_real_status -v
```

RED: exit 1, `Ran 2 tests in 2.969s`, `FAILED (failures=4)`. The actual shell loop visited only `vpn_revoke/m1` instead of all four rows. The actual original child read `vpn_loss\tm2\ntor_revoke\tm3\ntor_death\tm4\n` instead of EOF in all three subcases (original status 0, status 23, and wrong-nonce clock failure with status 23).

GREEN: exit 0, `Ran 2 tests in 3.136s`, `OK`. All four identities are visited; every actual Recorder child sees EOF with empty stdout/stderr and status 0. The original child sees EOF while retaining real original statuses 0/23 and the independently expected clock status 0/1.

The loop regression uses real Bash process-substitution input, real Recorder and real stdin-draining subprocesses, with no mocks. The existing host protocol harness uses a disposable fake ADB and simulates only process ownership snapshots for the local `/proc` namespace limitation. Popen, stdin, process completion, exit status, clock commands and raw recording remain real. These are host regressions, not Android runtime evidence.

## Focused validation

```sh
python3 -m unittest scripts.tests.test_evidence179_clock scripts.tests.test_evidence179_runner -v
```

Exit 0: `Ran 41 tests in 14.329s`, `OK`. Includes accepted I1 already-exited-child ownership failures with original 0/23, I2 exact original/injected argv and duplicate property rejection, raw deletion/relocation replay, independent clock upper guards, protocol failures/timeouts, and runner original/collection/clock/peer fail-closed barriers.

```sh
python3 - <<'PY'
import ast,hashlib,json
from pathlib import Path
for name in ('scripts/evidence179_clock.py','scripts/tests/test_evidence179_clock.py','scripts/tests/test_evidence179_runner.py'):
 data=Path(name).read_bytes();ast.parse(data,filename=name)
 print(hashlib.sha256(data).hexdigest(),name)
before=Path('/tmp/minimal179-stdin-before/scripts/evidence179_clock.py').read_text()
expected=before.replace('subprocess.run(argv,capture_output=True,timeout=timeout)','subprocess.run(argv,stdin=subprocess.DEVNULL,capture_output=True,timeout=timeout)').replace("subprocess.Popen(command,env={**os.environ,'ANDROID_SERIAL':serial})","subprocess.Popen(command,stdin=subprocess.DEVNULL,env={**os.environ,'ANDROID_SERIAL':serial})")
assert Path('scripts/evidence179_clock.py').read_text()==expected
assert Path('scripts/tests/test_evidence179_runner.py').read_bytes()==Path('/tmp/minimal179-stdin-before/scripts/tests/test_evidence179_runner.py').read_bytes()
cases=json.loads(Path('scripts/evidence179_cases.json').read_text())
assert len(cases)==153
assert len([c for c in cases if c['classname'].endswith('.EvidenceOwnedRoute179Test')])==4
print('Python syntax; exact two-launch-point change; runner tests unchanged; 153 cases / 4 routes: PASS')
PY
bash -n scripts/run_evidence179.sh
```

Exit 0. Python printed the three hashes below and `Python syntax; exact two-launch-point change; runner tests unchanged; 153 cases / 4 routes: PASS`; Bash syntax produced no output.

| File | SHA-256 |
| --- | --- |
| `scripts/evidence179_clock.py` | `4d43aa230f985633f084da06bcff6dd2e7d7619807e7e66ff1ca4270e050f8f9` |
| `scripts/tests/test_evidence179_clock.py` | `53a8efe627fb16d9e1099ebb8c106afabb8d3390500b124ea0986d5f4e8fb6cc` |
| `scripts/tests/test_evidence179_runner.py` (unchanged) | `3187e7613c4309937e0637b83b737cdc7b38d2b63a428f9bd91685fe637dee4a` |

Frozen for fresh narrow review. No Git, CI, local Gradle, signing, credential, workflow or Android execution actions were taken. The prior run's 150 passing original XML cases remain incomplete evidence: the missing three routes have not been executed by this correction locally, and no full 153-case, packet or release approval is claimed. Root owns the subsequent coherent candidate and existing minimal CI verification.
