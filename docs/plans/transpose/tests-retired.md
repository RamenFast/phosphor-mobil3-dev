# Retired tests

Plan rule: source-string and layout tests retire when their code is touched. Behavior tests stay.

| Test class | Test | Why |
|---|---|---|
| `AutoFrameWiringTest` | `(whole file)` | source-string file |
| `ManualContentTest` | `helpLabelsAndRangesAreLinkedToCurrentControlSource` | source strings |
| `AppearancePresentationTest` | `pauseLabelRemains… (source part only)` | source strings |
| `AppearanceRuntimeWiringTest` | `roomHasNoIdleClockAndBurnInReceivesActualVisibility` | source strings |
| `PictureInPicturePolicyTest` | `quickAndFullSettingsShareLiveStateAndManualActionsAreReachable` | source strings |
| `KnownDefaultsTest` | `sourceOnlyFiveKeyMatrixRetainsOneStateRestoreActionArchiveAndFullUiOwner` | source strings |
| `AppearancePresentationTest` | `actualConsumersUseOpaqueReadableRolesAndDensityWithoutShrinkingTargets` | source strings |
| `ControlsVisibilityPolicyTest` | `allQuickContentSharesOneBoundedScrollOwnerBeforeTopRemainderDismissal` | source strings |
| `StageGesturePolicyTest` | `realCardsReportAfterTheirTransformsAndBeforePadding` | source strings |

Replacement: device UX checks on the test bed (screens, gestures, readouts) plus host behavior tests.
