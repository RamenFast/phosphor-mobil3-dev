# Startup renderer reconciliation correction

## Finding and boundary

Blossom's full R16 round3 examines immutable mobile6355074. Its startup trace distinguishes two owners: Android recreation creates fresh UI defaults, while the process render loop can retain a nondefault LightCycle. If restore-time saving and rollback fail before native light publication, the new UI tuple is not a known active renderer tuple. The prior correction retains storage uncertainty but allows RETRY SAVE CURRENT to persist that unconfirmed tuple without a renderer receipt.

The original report remains unchanged. This correction uses the existing bounded instrument request/receipt mechanism. It does not invent a synchronous native snapshot, restart audio, block the UI thread or clear uncertain state just because persistence succeeds.

## Defined outcome

1. A failed startup light restore marks the displayed setup as an unconfirmed restore target. Its valid guarded light settings may be shown as targets, not confirmed active tuning.
2. This state blocks manual tuning, ordinary apply/undo/import and automatic saves. A pure storage retry cannot declare coherence.
3. The recovery action is labeled RESTORE DISPLAYED SETUP, explaining that the current target will be applied. It sends one existing exact instrument request and waits off-main through the existing finite receipt path.
4. Only a committed exact receipt publishes the complete target and removes startup uncertainty. Persistence follows that publication. No undo baseline is created from an unconfirmed pre-restore snapshot.
5. Rejection, cancellation, timeout, source supersession or closure keeps startup uncertainty. A stale completion cannot clear a newer recovery. Failed receipt confirmation retains the existing stronger unknown-native-outcome behavior.
6. If native reconciliation succeeds but persistence fails, the target is now known active. Existing RETRY SAVE CURRENT remains a persistence-only operation, with no native setters.
7. Existing known-active preset failures retain their present recovery behavior. Remote geometry capability checks, rapid-light guard, source authority and receipt release remain unchanged.

The Activity initializes its owner before restore as already corrected. Startup uses the Boolean result of the actual light transaction. Invalid stored light also cannot establish a known renderer tuple. It shows a valid displayed recovery target plus the decoding error, without overwriting invalid saved bytes before explicit recovery.

## Checks

Use the real pure InstrumentWorkflow with distinct retained-renderer and fresh-UI tuples. Inject failed initial save and rollback through SettingsWriteOwner. Assert zero storage-only success, one explicit request, no publication before admission, exact reconciliation before persistence, no untrusted undo, and equality of saved/UI/rendered after success. Cover cancelled/rejected/late receipts, capability denial, second storage failure and later no-native retry. Actual Activity/source checks tie initialization, failed restore and recovery labels to this owner.

Pure tests are not Android recreation, filesystem fault injection or JNI acceptance. The coordinator runs the full frozen Android gate after the unrelated settings writer releases its slot. Final R16 full review and device acceptance remain required.

## Pure implementation checkpoint, 12:54 UTC

The coordinator's actual-source cached Kotlin/JUnit run passed91 tests at12:52:58. This includes the five new distinct-renderer/startup cases and all retained suites selected by the runner. The previous fresh-owner test is now accurately named as a known-active storage-only recovery test. No runtime implementation was replaced by a test copy or adapter.

Evidence: `/home/ben/.jcode/scratch/instrument-startup-reconcile-1252.ZJYPnP`. Runner SHA256 `ae42e22b1e9839ec91a9a79878ebe565caed39f66b2c24fd0cae0c3a47eddfc0`, dependency-path manifest `bd2f6bf8a9631b2f352efae737c05e99d975e6c703054fa5359d3f4871002cbb`, test log `6491dbcb992dd53492980d43d255a292c2a0bbfb4565cb73f316685ef2213bc5`. `source-after.sha256` records the post-run source and uncompiled Activity adapters separately. The initial attempt to execute the read-only runner directly returned126, then explicit Bash execution passed. No permissions changed.

Android type checking, source-linked UI tests and the full gate remain pending the settings writer's release. This checkpoint is not a revised independent review score.
