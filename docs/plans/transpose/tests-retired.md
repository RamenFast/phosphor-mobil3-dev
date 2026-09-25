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
| `BackgroundLifecyclePolicyTest` | `settingsSourceWiringHasOneChipTheExistingAdapterAndImmediatePersistence` | source strings |
| `ForegroundBrightnessPolicyTest` | `persistenceImportAndUiStaySeparateFromAudioAndGlobalBrightness` | source strings |
| `InstrumentActivityWiringTest` | `sheetRoutesEveryPresetActionThroughActivityAndModeBansHaveNoUiBypass` | source strings |
| `DoubleTapPolicyTest` | `fullSettingsActionTraversesBothInterfacesAndRealAdapter` | source strings |
| `GridDataTest` | `actualHeartbeatSettingsAndVisibleBandAdaptersUseTheSameOwners` | source strings |
| `RotationDetentReachabilityTest` | `sourceOnlyFullSheetDisablesBothDependentControlsAndExplainsAndroidAuthority` | source strings |

Replacement: device UX checks on the test bed and primary phone (screens, gestures, large fonts) plus host behavior tests.
