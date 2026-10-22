# Independent regression review

Reviewed behavior: 932613495bd27ef06d9fca87810e9a56c10d26cd; formatting-only correction 64f904d7dd2976898e0aa56e03eb8c7a575c3985. Follow-up deletion-result delta reviewed for d053a0f6b654fc6dd76bec697d405002ea3611e7.

Independent read-only reviewer inspected active-vault vs Gallery authority, Phone transfer receipts, Primary-to-Hidden transfer, source removal, lifecycle cancellation, and the workflow. No additional blocking source finding remained after these corrections:

- Primary phone imports now use cancellation-aware acquisition and a guarded final index publication.
- Phone transfers capture destination, Primary key identity, presentation generation, foreground state and (for Hidden) its operation epoch.
- Only verified copies tied to the exact selected source URI can request MediaStore deletion. Sources are compared again by length and digest. Android confirmation owns the actual deletion; external source edits during an already-open system prompt cannot be atomically prevented by this API.
- Denied/failed/unavailable source deletion retains copies and reports originals retained. Unknown query results do not count as removal.
- Primary-to-Hidden copies authenticate streamed source and destination before guarded removal of the exact Primary source version into normal 30-day Recently Deleted. Explicit Move confirmation describes this recovery behavior. There is no automatic deletion replay after restart.
- Primary Gallery authentication retains Hidden selection and its independent key. Cancellation invalidates biometric attempts before cancelling the prompt. Explicit Return to Primary still revokes Hidden.
- Viewer/collection UI is cleared on navigation; Primary Gallery collection actions use Primary authority and presentation ownership.
- Production signer probing, release build and production artifact upload were removed from this branch's push workflow. No production signing secrets were accessed for this correction.

The follow-up review found no blocker in the extracted deletion-result handler. Empty receipts now report unavailable counts without claiming source retention or resuming deletion. Added tests exercise real MediaStore queries, denial, approval without observed deletion, observed deletion, and Activity recreation followed by a result callback. The platform confirmation dialog itself still requires physical acceptance.

Runtime acceptance is separate from this source review. Required physical checks include Android delete approval/denial, recreation during its prompt, and end-to-end PIN/biometric authentication. The existing composable Hidden Phone Copy path retains its independent repository/epoch guards and now verifies destination persistence; phone Move uses the new guarded receipt path.

The reviewer also confirmed 17334bc0d66f6ade752bebf759459c5dd7d9d9fe changes only disposable test cleanup: a redundant MediaStore deletion can fail after the row has already been removed. Tolerating that cleanup failure leaves test-body assertions intact and changes no production behavior. Final CI remains required.
