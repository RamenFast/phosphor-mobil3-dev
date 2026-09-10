# Fresh tactile default ASUS proof

2026-09-10. HEAD 0a72026 (source `1921b38`). NAAIB70036673ZC. MUSIC 1/30. Prefs restored byte-for-byte after both launches.

## Empty install

Both preference files replaced with empty `<map/>`. First launch wrote `appearance_state` with `"look_version":2` (AMOLED engraved tactile).

## Upgrade-like prefs

Original baseline has `gain` and no `appearance_state`. First launch did not write `look_version` 2. Active appearance omitted `look_version` (codec omits 1). Saved looks were not rewritten to tactile.

## Restore

Original two XML files restored after the matrix. No leftover Phosphor services.
