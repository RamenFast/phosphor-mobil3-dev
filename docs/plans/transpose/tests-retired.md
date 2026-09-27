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

## Redesign slice a (Settings flat) · Designer · 2026-09-25

| Test class | Test | Why |
|---|---|---|
| `InstrumentActivityWiringTest` | `startupCreatesRecoveryOwnerBeforeAnyRestoreTimeLightWrite` | source strings of the retired INSTRUMENT sheet / old SheetHost header text |
| `InstrumentActivityWiringTest` | `typedNativeRefusalReachesSourceNavigationWithoutStatusStringInference` | source strings of the retired INSTRUMENT sheet / old SheetHost header text |
| `ManualContentTest` | `gestureDiagnosticRateAndPresetAnswersMatchImplementedOwners` | source strings of the retired INSTRUMENT sheet / old SheetHost header text |
| `SheetEntryPolicyTest` | `sourceOnlyHeaderNestedAndExplicitClosesKeepTheirExistingOwners` | source strings of the retired INSTRUMENT sheet / old SheetHost header text |

## Redesign slice b (LIGHT) · Designer · 2026-09-25

| Test class | Test | Why |
|---|---|---|
| `KnownDefaultsTest` | `sourceOnlyLightLegRangeAndPhotosensitivityConfirmationRemainOwnedByLightSheet` | source strings of the old LIGHT text rows; range and timer/track now covered by LightChoicesTest; guard by LightCycleGuardTest |

## Redesign slice a2 (shared dismissal) · Designer · 2026-09-25

| Test class | Test | Why |
|---|---|---|
| `AppearancePresentationTest` | `resumeRefreshesObservableMotionAndPostUsesTestedCancellableVisiblePolicy` | source strings of the retired Settings dismissal adapter / expander |
| `AppearanceRuntimeWiringTest` | `editorHasExactInputsAccessibleActionsAndRootOwnedChildGestureHook` | source strings of the retired Settings dismissal adapter / expander |
| `SheetEntryPolicyTest` | `sourceOnlyHostUsesTheGuardedOwnerAndItsCommittedRenderedOffset` | source strings of the retired Settings dismissal adapter / expander |
| `SheetEntryPolicyTest` | `sourceOnlyFirstCommitLatchesCurrentEntryBeforeClosingWithoutResettingLiveOffset` | source strings of the retired Settings dismissal adapter / expander |
| `SettingsGestureAdapterTest` | `(whole file, 43 tests)` | its code (SettingsGestureAdapter) retired: all sheets share one scroll-then-pull mechanism |
| `SettingsInteractionTest` | `(whole file, 15 tests)` | its code (SettingsDismissOwner, SettingsPresentationOwner) retired with the adapter and the expanders |

## Redesign slice c (LOOK) · Designer · 2026-09-25

| Test class | Test | Why |
|---|---|---|
| `AppearanceRuntimeWiringTest` | `ordinaryAppearanceActionsHaveNoNativeTuningOrInstrumentEditAuthority` | source strings of the retired ROOM sheet / live style sample |
| `RoomStyleOverrideTest` | `productionProviderAndSingleSampleReadEffectiveStyleAndCurrentState` | source strings of the retired ROOM sheet / live style sample |
| `RoomStyleOverrideTest` | `roomTilesAndFullSpanStyleSampleShareOneBoundedScrollOwner` | source strings of the retired ROOM sheet / live style sample |
| `SettingsControlAccessTest` | `settingsOnlyContextKeepsLegacyLayoutsAndPointerSetters` | source strings of the retired ROOM sheet / live style sample |
| `AppearancePresentationTest` | `activityAndRoomUseTheTestedCurrentAdapterNotSavedRecordsOrLegacyTuples` | source strings of the retired ROOM sheet / live style sample |
| `RoomStyleOverrideTest` | `sampleReadsAnimatedOffsetDuringPlacementInsteadOfComposition` | source strings of the retired live style sample |
