# Step 3: essentials

The goal of this step: the scope is easy to see and easy to steer while it is being used as a
music visualizer, including in a car. One item at a time, each one ending in a build on a phone.
The design language stays as it is today; the settings redesign is step 4.

Quotes are Ben's, 2026-11-13.

## Order

1. Dim after returning to the app (bug)
2. Fixed brightness
3. More zoom, and a zoom dial
4. "Open external player" button
5. HDR beam (decided after a check that rides in the first build)

## 1. Dim after returning to the app

> there is a long standing bug when switching apps/opening settings, it's like the settings are
> still open and the scope is dimmed until after like 3 seconds of the main screen being looked
> at or a physical tap to restore the scope clarity/brightness.

State: not reproduced. A home-and-back test with fullscreen off and controls always visible
showed three equally bright screen grabs (before, 0.9 s after return, 5.9 s after).

Leads, none proven:

- Fullscreen and auto-hiding controls both end "after a few seconds or on a tap", the same
  clock as the bug. Both were off during the test.
- The dark veil behind a sheet has no fade of its own. It is removed only when the sheet's
  closing animation reports that it finished (`ui/Sheets.kt`, `SheetHost`).

Ruled out by reading: the keep-awake flag, the 1.2 s warm-up (it runs only when the renderer
is first made), every timer in the Kotlin code except the 4 s controls timer.

Next: reproduce with fullscreen on and auto-hiding controls, take a screen grab while it is
dim. If the grab is dim, the cause is a layer the app draws. If the grab is normal, the cause
is the display.

## 2. Fixed brightness

> a "fixed brightness" feature (if android supports it). a button then a brightness slider
> below it. A lot of phones have automatic brightness, mine included, and I feel like having it
> fixed to something I set it to (actual screen brightness) would be awesome.

- A button (on or off) and a slider under it, in SETTINGS, DISPLAY.
- Android applies a window's brightness only while that window is in front, so the phone's own
  setting returns when the app leaves. No permission is needed.
- Moving the slider turns the feature on.
- Saved with the other settings and carried by settings export.
- To measure on the S25: whether the top of the slider reaches the outdoor boost that
  auto-brightness can reach.

## 3. More zoom, and a zoom dial

> to pickup certain subtle sounds We might need like 25x zoom in. Up from 7x yes. The .1 zoom
> out seems to be good. And for this addition aswell, we need the up/down single finger zoom
> and *multi finger* pinch zoom working well/playing nicely together. If there is another
> swipe/control surface that needs to be removed in order to do that that's quite alright.

> while the times multiplyer next to the finger is nice/looks cool, it's hard to read at times,
> if we has some *vector style graphics*, think like half of a circle coming in from the right
> side of the phone with the times/zoom multiplyer that goes away when at the zoom level the
> user wants, that's easy to read, and the half circle/vector/grid style? [...] graphics taking
> up 2/3rds of the width of the display *about*. Kinda like a *dial*...

- Zoom range becomes 0.1× to 25× (today 0.1× to 7×).
- One-finger up/down and two-finger pinch both zoom and must not fight. Another swipe may be
  removed to make room.
- The readout becomes a dial: half a circle that comes in from the right edge, about two thirds
  of the screen width, drawn in the scope's own vector and grid style, with the multiplier in
  large figures. It leaves when the fingers lift.

## 4. "Open external player" button

> we'll add a "open external player" button, that will integrate whatever album art, media
> controls, metadata etc. that we can while still letting whatever somebodies preferred app is
> be its own thing.

Capture already mirrors another app's title, art and transport. The button opens that app.

## 5. HDR beam

> Is HDR also a potential button we could have in order to make the beam brighter?

> they should be able to interact/stack. "the higher the fixed brightness, the less room HDR
> has above it." might be a useful "?" help box tip, UI ideas *(for later)*

- The S25 screen reports HDR10, HDR10+ and HLG. Thin bright lines on black suit HDR.
- Today the scope draws an 8-bit standard surface and its brightest point is white.
- Unknown: whether the phone's graphics driver offers the renderer an HDR surface. The first
  build of this step logs the surface formats the driver offers; the decision follows that.
- Fixed brightness and HDR stack. The higher the fixed brightness, the less room HDR has above
  it; that sentence is a candidate for a "?" help tip later.

## Parked for step 4

- A developer control surface for debug builds: set source, mode and theme and read state back
  without taps. The Play build stays closed.
