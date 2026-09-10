# Tactile track console — F1 still open (look 1)

2026-09-10. Muse visual round 1 **7/10**.

Baseline ASUS prefs have no `look_version` (legacy **look 1**). `ConsoleKeybedPolicy.tactile` is look 2 only. The Muse shots and later APK installs were FlatKey `◂◂`/`▸▸` plus StoneKey play, not `TactileConsoleKeybed`. Painting the tactile well red did not appear. Skip-pair source and key-face `drawConsoleVector` therefore never showed.

Look 2 source is ready: `drawConsoleVector` on the key face, 26dp box, PLAY-band skip pair (`15742d2` plus this commit). Device proof needs CONSOLE KEYS · TACTILE (look 2), then playing+ended crops, then Muse round 2. Restore prefs after.

Prefs restored. Fixture removed. MUSIC 1/30. S25 untouched.

## Closed, 2026-09-10 later
Look 2 shots Muse **9/10**. Round 1 kept as look-1 history. Prefs restored.
