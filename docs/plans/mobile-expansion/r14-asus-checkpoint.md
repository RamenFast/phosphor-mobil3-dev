# R14 ASUS fresh-launch / process-death matrix
2026-09-10. NAAIB70036673ZC. HEAD 3f48475 (R14 source ca0c63d). MUSIC 1/30. Prefs restored. RECORD_AUDIO revoked.
## Results
- 1 fresh none last none RECORD_AUDIO false: ['ServiceRecord{1873436 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']
- 2 last_source mic default missing RECORD_AUDIO false: ['ServiceRecord{d465e68 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']
- 3 last_source mic default missing RECORD_AUDIO true: ['ServiceRecord{10fe124 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']
- 4 default mic unconfirmed RECORD_AUDIO true: ['ServiceRecord{6d479e2 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']
- 5 default mic confirmed RECORD_AUDIO true fresh: ['ServiceRecord{1825641 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}', 'ServiceRecord{db7951b u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.MicCaptureService}']
- 6 same-process clear-task after confirmed mic: ['ServiceRecord{1825641 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']
- 7 process-death confirmed mic: ['ServiceRecord{ec3d60d u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}', 'ServiceRecord{8a897b3 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.MicCaptureService}']
- 8 default capture confirmed popup off: ['ServiceRecord{6edc4c2 u0 dev.phosphor.mobil3.debug/dev.phosphor.mobil3.PlaybackService}']

## Read
- last_source is not a default. Mic did not auto-start from last_source=mic with or without RECORD_AUDIO.
- Missing default_source is none. Fresh launch only PlaybackService.
- Unconfirmed default mic does not auto-start.
- Confirmed default mic + RECORD_AUDIO starts MicCaptureService on fresh launch and after process-death.
- Confirmed default capture with popup off does not start MediaProjection.
- `--activity-clear-task` in the same pid stopped MicCaptureService. That is not rotation. Manifest configChanges skips orientation recreate. Rotation not exercised.
- Root auto-start still deferred.
