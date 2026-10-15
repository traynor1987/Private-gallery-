# Checkpoint infrastructure recovery


## Infrastructure loss qualification — checkpoint upload

After every final verification completed, the execution service lost the original
/workspace/scratch/0881340dd615 directory during blob upload. Shell execution from
/ was available, but the original worktree, build outputs and unuploaded logs were
no longer present. The recovery branch had not been advanced.

All nine changed production/Kotlin-test files had already been uploaded with exact
original staged Git blob hashes. The Python guard was recovered byte-for-byte and
its GitHub blob SHA matched the original staged hash. All ten changed source/test
files therefore remain exactly those tested. The original baseline is unchanged.
Documentation/evidence-only recovery changes do not replace production source.

Some raw Task3B logs, lint XML and per-class full-suite XML summaries could not be
uploaded. References above to their retention are superseded by this notice. No
missing raw file was recreated or represented as available. See
CHECKPOINT_RECOVERY_NOTICE.md and original-stage-inventory.json in the evidence
folder for the exact available/lost inventory and observed verification results.
The observed completed test/build results remain real; the raw archive is incomplete
and independent replay/reverification remains an additional acceptance obligation.
Task3B stays PARTIAL, full acceptance BLOCKED; Task3C must not begin.

The source checkpoint is recoverable and exact. The evidence archive is partial;
reproducing missing raw verification records requires a restored build environment.
The original branch was not advanced until source recovery and this qualification.
