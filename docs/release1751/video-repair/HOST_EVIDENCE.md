# Independent temporary host evidence

RED and GREEN compile the actual copied VaultVideoDataSource and ChunkedVaultVideoStore. StoredPayload is its verbatim declaration extracted from EncryptedPayloadStore. Android Uri/annotation and Media3 transport types are minimal compile adapters; actual encrypted read/header/tag/close logic is not mocked. Source hashes are retained.

RED: exit1, revocation after successful precheck returns known plaintext; session close leaves reader key and 1MiB cache until DataSource closes.
GREEN: exit0, same after-open revocation arm rejects/wipes; real retained reader key/cache wipe on session close; post-close read rejects/wipes; genuine AEAD corruption crossing a chunk boundary rejects and wipes requested destination. Compiler warning only (unnecessary !!), no compile error.

Compile command used Java org.jetbrains.kotlin.cli.jvm.K2JVMCompiler with Gradle9.5.0/lib/* compiler classpath, -no-stdlib -no-reflect, Kotlin stdlib2.3.20 +annotations24.0.1 explicit classpath. Run main RevokeRedKt (RED) / GreenKt (GREEN), passing a disposable synthetic fixture-directory path. Never run on owner media. RED/GREEN source snapshots differ intentionally; hashes identify them.

Hidden benchmark compiles the unmodified actual HiddenChunkStore.java with Java17 compiler and Bench.java. 16MiB synthetic bytes verified after every read, 3 timed samples/row. Exact aligned-read decryption amplification is derived from implementation; not an instrumented crypto counter. Host timings are noisy and exclude repository metadata work, Android provider/storage/thermal behavior. They do not establish owner-video starvation or justify a cache change.

Only synthetic fixture data and constant synthetic keys were used. Fixture payloads were removed. No repository changes or CI by reviewer. The sole writer should persist this evidence with the reviewed source checkpoint; /tmp alone is not durable.
