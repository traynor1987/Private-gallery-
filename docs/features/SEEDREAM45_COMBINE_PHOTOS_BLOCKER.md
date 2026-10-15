# Seedream 4.5 Combine Photos — security admission blocked

Reviewed 2026-10-15. **No feature implementation, build pass, or owner acceptance is claimed.**

## Scope and preserved state

The requested feature is an extension of AI image creation: choose 2–14 existing
Vault photos, inspect and reorder them, edit a preset or custom prompt, generate
one image with Seedream 4.5, preview it, then explicitly save an encrypted copy.
Originals must remain untouched. Selected references leave the device for Replicate.

Read-only recovery established these remote states:

| Reference | SHA / state |
| --- | --- |
| `main`, initial feature baseline | `93ed56fbfcd6cb04731a87452dbcbf2e6d6bcb10` |
| `phase3/verified-hidden-media-transfer` | `79bb1928f3fbe16e935e587a3fe594158cb3f529` |
| `recovery/phase3-task3-wip-2026-10-06` | `9e9129e0f3db27fca00ef7f7214446c9c28c0553` |
| PR #59, fetched through GitHub integration | OPEN, DRAFT, UNMERGED; base is the main SHA above |

Phase 3's exit checklist says NO-GO. Its current PR description says Task 3
is incomplete. The recovery branch contains newer work and must not be replaced
with the older PR head. Neither branch, PR #59, nor main was modified here.
The dedicated branch contains this report only.

## Provider contract

Official Replicate version API documentation describes `image_input` as an array
of 1–14 images for single or multi-reference generation:

https://replicate.com/bytedance/seedream-4.5/versions/e563f451edd459e76456dc2b32d7d2c02176059629759923965f9e86494325f6/api

The feature should require 2–14 selections, maintain their order, and request
`sequential_image_generation: disabled`, `max_images: 1`. No paid API call was
made. Account-specific request-size admission and real 14-reference execution
have not been tested. Model schema support alone does not prove practical
transport, memory, or account limits.

## Existing extension points

Paths below are relative to `app/src/main/java/uk/co/traynor/privategallery/`.

- `ui/CreateImageSheet.kt`: existing model picker, editable prompt, progress,
  cancellation, consent dialog and Vault reference picker for Seedream 5 models.
- `core/editor/ReplicateImageGeneration.kt`: shared creation adapter,
  `GenerationModel`, `GenerationRequest`, polling, typed errors and cancellation.
  Seedream 4.5 currently has `maxReferences == 0` and no `image_input` mapping.
- `core/editor/ReplicateSeedreamProvider.kt` and `ReplicateSeedreamApi.kt`:
  existing single-image editing adapter, using the same stored Replicate token.
- `MainActivity.kt::generateVaultImage`: scoped reference validation/read,
  remote request, output sanitization and encrypted import.
- `core/vault/AndroidVaultRepository.kt::importAiGeneratedImage`: verified
  encrypted acquisition with remote-AI provenance and a distinct output copy.

The current creation flow imports before opening the viewer. It does **not**
already implement the requested preview-before-Save boundary.

## Blocking security findings

1. **Reference copies cannot be explicitly cleared in the current creation path.**
   `MainActivity.generateVaultImage` prepares each selected image, then calls
   `Base64.getEncoder().encodeToString(prepared)` and retains data-URI Strings.
   The original/prepared byte arrays are wiped, but those immutable encoded
   copies are not. `GenerationModel.input` puts them into a JSON array, and
   `ReplicateImageGenerationApi.generate` serializes the JSON into another
   String and UTF-8 array. The serialized request array is not explicitly wiped.
   This is an in-memory cleanup limitation, not evidence of public disk leakage.

2. **The approved newer cleanup machinery has not admitted reference-bearing
   generation end to end.** On both inspected Phase 3 references,
   `docs/phase3/RUNTIME_MIGRATION_LEDGER.md`, the
   `core/editor/AiHttpTransport.kt` row explicitly leaves reference-bearing paid
   submissions and final image handoff pending. Reviewed text-only Seedream 4.5
   admission explicitly does not admit image edits. The same ledger leaves
   complete credential, producer, immutable-copy, image input/output and measured
   whole-caller cleanup capacity obligations unresolved. Copying text-only
   admission or retaining the old reference path would not satisfy those gates.

3. **A new pending-result preview needs its own admitted lifecycle.** The current
   callback returns an already-imported VaultItem. Holding plaintext output for
   a Save decision requires bounded owned bytes/bitmaps, revocation and stale
   callback denial through lock, backgrounding, dismissal, failure and recreation.
   No such preview-before-save implementation or evidence exists on this baseline.

The simple reuse-only implementation cannot establish the requested cleanup
guarantees. Resolving these findings requires extending and verifying the same
ownership boundaries still under active Phase 3 migration. This report does not
claim the feature is fundamentally impossible; it records why it was not enabled
by extending the older path or importing unfinished Phase 3 code.

## Required continuation boundary

Once the shared reference-bearing producer and output ownership boundaries are
admitted, isolate this feature from that verified baseline and reuse them. Stream
ordered bounded references without creating immutable image data URIs; preserve
credential configuration, original scope checks, polling and cancellation.
Establish practical per-image and aggregate budgets before decrypting references.
Preview must not imply saved, and Save must recheck the original live authority.

The UI can then extend the existing creation sheet with a Combine Photos mode,
ordered thumbnails, remove/earlier/later controls, selected count, explicit remote
disclosure and these editable presets: People Together, Background Replacement,
Object Combination, Creative Scene and Custom. People prompts should request
facial/hairstyle preservation, realistic proportions, coherent lighting and no
duplicate anatomy, without promising exact identity preservation.

## Verification and delivery status

- Local checkout was clean before adding this documentation. No production,
  test, build, workflow, release or signing files were changed.
- GitHub combined-status and commit-workflow queries for the main SHA returned
  empty lists. They do not establish a passing CI baseline. Historical Phase 3
  test claims were read as historical evidence only, not rerun here.
- `./gradlew --version` failed downloading Gradle 8.10.2 from
  `services.gradle.org`: `java.net.SocketException: Network is unreachable`.
  A second attempt with the configured proxy properties also failed. An Android
  SDK was not found in the checked standard locations. No Kotlin compilation,
  Android build, JVM tests or instrumentation ran in this execution.
- No paid provider generation, owner photographs, physical device test or
  release APK was used or produced.
- Pending tests: 2/3/14 references and ordering, invalid counts, request failures,
  ambiguous submission/download timeout, cancellation, backgrounding, auto-lock,
  exact buffer/resource retirement and filesystem residue, preview discard and
  secure output import, plus existing single-image generation regression.

**Result: BLOCKED, not implemented, not ready for owner physical acceptance.**
This follows the mission's explicit instruction to document a security blocker
rather than weaken protection or interfere with Phase 3.
