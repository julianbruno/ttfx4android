# Feature: Batch 4 Sweep & Atmospheric Effects Port

## Objective
Port the 14 remaining atmospheric, field, scan, and glitch sweep effects from Swift to Kotlin in `com.ttfx.effects` and implement the complete 37-effect `EffectRegistry`, achieving deterministic parity and completing the full effect catalog.

## Scope & Constraints
- Implement 14 effects: `BeamsEffect`, `BurnEffect`, `ColorShiftEffect`, `HighlightEffect`, `MatrixEffect`, `RainEffect`, `SmokeEffect`, `SpotlightsEffect`, `SweepEffect`, `SynthGridEffect`, `ThunderstormEffect`, `VHSTapeEffect`, `WavesEffect`, `WipeEffect`.
- Implement `EffectRegistry` with all 37 effects.
- Pure Kotlin, zero Android SDK dependencies.
- Primary constructor convention: `(configuration: EffectConfiguration, private val canvas: Canvas, input: InputText, seed: ULong, val options: Configuration = Configuration()) : Effect`.
- No duplicate `canvas` or `options` fields.
- Exhaustive `when` expressions.
- Tests in `Batch4SweepEffectsTest.kt` verifying deterministic completion, frame parity for `rain.frames` and `wipe.frames`, and registry completeness.

## Implementation Tasks
- [x] TASK-01: Port `BeamsEffect` (beams)
- [x] TASK-02: Port `BurnEffect` (burn)
- [x] TASK-03: Port `ColorShiftEffect` (colorshift)
- [x] TASK-04: Port `HighlightEffect` (highlight)
- [x] TASK-05: Port `MatrixEffect` (matrix)
- [x] TASK-06: Port `RainEffect` (rain)
- [x] TASK-07: Port `SmokeEffect` (smoke)
- [x] TASK-08: Port `SpotlightsEffect` (spotlights)
- [x] TASK-09: Port `SweepEffect` (sweep)
- [x] TASK-10: Port `SynthGridEffect` (synthgrid)
- [x] TASK-11: Port `ThunderstormEffect` (thunderstorm)
- [x] TASK-12: Port `VHSTapeEffect` (vhstape)
- [x] TASK-13: Port `WavesEffect` (waves)
- [x] TASK-14: Port `WipeEffect` (wipe)
- [x] TASK-15: Implement `EffectRegistry` (all 37 effects)
- [x] TASK-16: Add comprehensive unit and parity tests in `Batch4SweepEffectsTest`

## Verification Evidence
- 14 effect implementations written to `effects/src/main/kotlin/com/ttfx/effects/`:
  - `BeamsEffect.kt`
  - `BurnEffect.kt`
  - `ColorShiftEffect.kt`
  - `HighlightEffect.kt`
  - `MatrixEffect.kt`
  - `RainEffect.kt`
  - `SmokeEffect.kt`
  - `SpotlightsEffect.kt`
  - `SweepEffect.kt`
  - `SynthGridEffect.kt`
  - `ThunderstormEffect.kt`
  - `VHSTapeEffect.kt`
  - `WavesEffect.kt`
  - `WipeEffect.kt`
- `EffectRegistry.kt` implements all 37 effects:
  - `allEffects` and `names` list all 37 lowercase names.
  - `contains(name)` lookup is case-insensitive.
  - `create(...)` / `makeEffect(...)` dynamic instantiators factory map.
- Unit and parity tests in `Batch4SweepEffectsTest.kt`:
  - `rainEffectMatchesAdmittedRustFramesFixture` passes 32-frame parity against `rain.frames`.
  - `wipeEffectMatchesAdmittedRustFramesFixture` passes 32-frame parity against `wipe.frames`.
  - Deterministic completion tests for each of the 14 effects.
  - Deterministic repeatability test for identical seed outputs.
  - Registry completeness and factory instantiation tests for all 37 effects.
