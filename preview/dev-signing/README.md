# Public preview development key

This is an intentionally public Android Debug key for the standalone experimental preview only. It is not a secret, not a production identity, and must never sign production NovaOS. Keeping this test key stable prevents future ephemeral CI runners from changing the preview certificate. Do not trust this certificate for sensitive data or distribution outside this owner preview.

Certificate SHA-256: 81cda5b72cb76940fa50972bc1c284b25e641b88814d2b9041c7111cb1d042d8

The first GitHub prerelease was signed by a different, unsaved runner-generated debug key (a5398358…). That key is not recoverable from an APK. Android will refuse a data-preserving update from that certificate; never auto-uninstall or clear data to bypass it. Production NovaOS is a separate package and must remain installed. Existing Library/local preview builds signed by this key can update normally.
