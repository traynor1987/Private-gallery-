# Owner evidence report and actual Home status repair

Owner specification in this session authorizes implementation on the existing integration
branch. Reuse Evidence Locker, authentication, encryption, OpenAI transport and Home appearance.
No private evidence is sent during development; tests use synthetic fixtures only.

## Evidence report

Extend the existing one-shot exact-payload consent flow with Task.EVIDENCE_REPORT and a
versioned strict report stored inside the existing encrypted Analysis.text envelope. No Catalog,
Codec, encryption or credential migration. Existing advisory tasks remain readable/available.
Only the owner-selected records and explicitly selected images can enter the preview. Preview
shows all transmitted content, metadata claims, model/options and instructions before a single
explicit send. Changing selection, owner/session, route or catalogue retires consent; retries
require a fresh preview. Credentials and plaintext evidence never enter logs or disk caches.

Locally validate every cited evidence ID, field, literal passage and timestamp quote against
the selected preview corpus. Preserve OCR/correction/metadata provenance and incomplete flags.
Images lacking local text anchors cannot create unverifiable counted accusations. Never invent
missing dates/messages/intent. Report separates factual quoted evidence, contextual inferences
and AI interpretation; includes qualified assessment/outcome (including Insufficient Evidence),
confidence, alternatives and limits. Chronology uses known source timestamp claims with their
provenance, leaving unknown/ambiguous dates explicit. Ordinary disagreement alone is insufficient.

Categories: emotional abuse, verbal abuse, blackmail/coercion, gaslighting, threats, intimidation,
manipulation. Count deduplicated potential events locally, with a set of categories per event;
explain overlap. Deduplicate exact originals and repeated/overlapping excerpts conservatively,
keeping all source references and noting ambiguity. Every assessment/count carries an asterisk.
The application injects the owner's exact disclaimer:

"* AI-generated assessment based on the selected evidence. Findings are interpretations, not guaranteed facts, clinical diagnoses or legal determinations. Context may be incomplete, and classifications or counts may be inaccurate."

Display and reopen reports from encrypted analysis history. Open supporting originals through
the existing authenticated viewer and immutable source fingerprints. Save uses the existing
encrypted catalogue boundary. Export report text separately, with explicit confirmation, through
the existing safe document handoff: retain only ID/digest across the picker, reacquire the same
Space, revalidate, and stream report-only output. Never export original media automatically.
Preserve original bytes, hashes, metadata and Gallery visibility choices.

## Status repair

Code59 APK contains both Home calls and the real provider; omission from build is ruled out.
Existing tests forced hidden bars in an isolated ComponentActivity with a fake provider. Add
actual Main/Home tests with the real Android provider and SpaceContext before a bounded fix.
Capture original window insets/lifecycle/interactive state and rendered bounds; test initial
display, real BatteryService broadcasts, pause/resume, native-app return, compact/wide/landscape
configurations, and visible Android bar suppression. Preserve callbacks, permissions and theme.
Do not assume the Samsung-only cause or claim physical-device verification without evidence.

## Delivery

Use the next verified unused code after59, unchanged package/original certificate, and existing
runtime/signing lanes. Host/JVM/build first; one focused combined API37 verification after any
needed diagnostic reproduction. Independent security review and actual signed59-to-next
encrypted-data preservation/restart gate publication. Physical Fold acceptance remains explicit.
