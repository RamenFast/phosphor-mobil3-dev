# Phosphor: a living signal instrument

Phosphor turns sound into a beam you can watch, shape, and carry.

The instrument should feel immediate. Audio moves. The beam answers. Controls appear when the user asks for them and leave the signal alone when the user watches.

## What Phosphor is for

Phosphor serves three honest source paths:

1. It plays local music and draws the exact audio it sends to the device.
2. It listens to a microphone or Android playback-capture session after clear user consent.
3. It acts as a remote head for a PC relay selected by the user.

These paths share one renderer and one visual language. The source may change, but the instrument does not become a different product.

## The signal comes first

The scope owns the display. Chrome stays restrained and dimensional. Touch reveals controls without making the interface feel like a dashboard placed over a visualization.

Motion explains ownership and direction. A sheet follows the finger. Rotation respects Android system authority. Reduced-motion settings remove flourish without hiding state changes.

The beam remains truthful. It does not invent frames, claim audio that Android withheld, or label a disconnected link as silence.

## Capture without deception

Android playback capture begins only after the user approves the system prompt. Phosphor asks for the complete display where Android supports that request.

Some applications and protected media opt out of capture. Silence from those sources is a platform boundary, not a connection success claim.

Phosphor does not use root, Shizuku, ADB, or privileged capture to bypass that boundary.

## Remote playback inside the user's walls

The PC relay lets the phone browse, play, hear, and visualize audio from a selected machine.

The relay belongs on a trusted local network or Tailscale. Phosphor does not present the current raw TCP protocol as safe for the open internet.

The app remains quiet on the network until the user chooses a remote action.

## Privacy by absence

Phosphor has no analytics, behavior tracking, advertising identifier, installation identifier, or silent reporting path.

Local settings exist to restore the instrument. Remote host data exists to make a requested connection. Neither becomes a profile of the user.

## One product

Phosphor ships as one Play-safe application. Debug builds may expose developer receipts, but production behavior does not split into public and privileged personalities.

Developer tools may build, install, test, and inspect Phosphor. They do not create an agent authority inside the product.

## Future growth

Future core features must strengthen the instrument without weakening its honesty, privacy, or immediacy.

An archived plan is not a promise. A source file that happens to remain is not a promise. A future feature becomes real only after the vision, specification, implementation, and acceptance checks agree.

## Non-negotiable invariants

- The picture follows the audible source.
- The UI states the difference between silence, disconnection, and unavailable capability.
- Consent precedes capture.
- The app makes no unsolicited network connection.
- The app contains no analytics or behavior tracking.
- One production package and one release process define the product.
- Working local and PC playback survive cleanup.
- The exact release source, signature, artifact, and installed package remain traceable.
