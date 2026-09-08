# Preset startup recovery correction

Round2 source review found that initial `restoreTuning()` can fail a light write and its rollback before `initializeInstruments()` creates the shared recovery owner. A nullable forwarder then loses the typed failure.

Create the existing workflow before restoring tuning. Its constructor does not publish or read a setup. Restore-time light transactions then use the same edit and typed recovery path as later user edits. Do not create a second latch or replay queue. Preserve the original restoration order within `restoreTuning()` and all successful-startup behavior.

Verify initialization precedes restore in the actual Activity adapter. Exercise a fresh production workflow with a real `SettingsWriteOwner` failed commit and rollback, then prove apply, import, manual edit and automatic save remain blocked until explicit complete-save recovery. These host and source checks do not prove Android filesystem fault behavior.
