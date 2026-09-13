# Final mode-order closure check

## Verdict: BLOCKED on the landing regression fixture only

The production source move is correctly ordered. In `rust/src/render.rs`, the actual tube-flip assignment at lines 851-854 precedes the single guarded AUTO update at lines 877-888. Grid setup follows at lines 890-897, then local DSP computation at line 963. `auto_gain.update(&samples, ...)` occurs exactly once in this render loop.

The existing guards remain intact:

- local AUTO requires `source_active && !geometry_active && auto_gain.enabled()`;
- manual mode therefore does not update AUTO gain;
- absent local source and remote geometry both take the rebase branch;
- HOLD presents the retained image and continues at lines 704-763, before the stereo drain and AUTO block.

The new `animated_mode_landing_selects_safe_gain_before_first_rotated_compute` regression checks source order, one update, both `Xy45` and `XySwirl`, first-frame protection, nonempty geometry, and gradual upward restoration. However, its landing fixture is `vec![0.005; 960]`, which contains only equal positive channel pairs. It does not exercise the required anti-equal/opposite-channel landing case. The separate `rotated_xy_framing_keeps_both_channel_corners_inside_with_margin` test has equal/anti-equal samples, but it does not reproduce the animated landing update order.

**Exact remaining blocker:** exercise both equal and anti-equal/opposite channel extrema through the first landed AUTO update for both rotated modes in the animated landing regression. No production-order defect remains.

The supplied native log is complete and reports the named regression passing within `134 passed; 0 failed`. No command, test, build, device, network, Git operation, source edit, or worker was used for this check.

## Checked SHA-256

- `build/recovery-unit1-baseline/review-runtime-corrections.md`: `944c32ddda70d26d62c1358e658ebdd1a5bdf3d69d0f21feae9ea38d391a3efa`
- `rust/src/render.rs`: `1cdc8be0c7ee50a1f33700c07b655dfecb34fb11f579a2f7bbd2a1f38a5e7556`
- `rust/src/engine.rs`: `9b117f34c4d8295819bc7d6e90deb47ed9e283fa120ac3fce0368d259c92ab52`
- `dev/scratch/recovery-unit1-20260913/native-tests-6.log`: `3b5b0cfcfb074faa4f9cb4120b953bac846c2097ddea6ec6605e08aab290ed66`

Review complete. Reviewer released with no continuing work.
