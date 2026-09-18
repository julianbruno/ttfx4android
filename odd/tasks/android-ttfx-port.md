# Feature: Android TTFX Port (37 Effects, Core Engine, Compose UI & Shaders)

## Objective
Port the `ttfx` terminal text effects engine to Android / Kotlin, achieving 1:1 behavioral and visual parity with the reference Swift and Rust implementations across all 37 effects. Follow TDD throughout the port.

## Scope & Constraints
- **Core & Effects Engine**: Pure Kotlin (`kotlin("jvm")`), zero Android SDK dependencies for fast host-side unit testing.
- **Rendering**: Jetpack Compose Canvas (`ui-compose` library module) with Glyph Atlas rendering.
- **Shaders**: AGSL (Android Graphics Shading Language) post-processing shaders (CRT, glow/bloom, glitch) for API 33+.
- **Parity Oracle**: Reference Rust/Swift implementations and `.frames` golden fixtures from `/Users/julian/miscodigos/ttfx`.
- **TDD Mode**: ENABLED (explicit user instruction).
  - Test runner: JUnit 5 (`org.junit.jupiter`) on JVM.
  - TDD cycle: RED (test fails / asserts expected golden frames) -> GREEN (implementation passes) -> REFACTOR.
- **Engram Mirror**: Pending (Engram MCP server not configured in current session).

## Delivery Strategy
- Strategy: `ask-on-risk`
- Authorize work-unit commits per task.

## Implementation Tasks

- [ ] **TASK-01**: Project Scaffolding & Build Configuration
  - Route: Delegated direct (Writer trigger: touches multiple Gradle and configuration files)
  - Details: Initialize Gradle 9.5.1 wrapper with JDK 21 toolchain, multi-module layout (`core`, `effects`, `ui-compose`, `app`), JUnit 5 dependencies, and Android Jetpack Compose setup.
  - Checks: `./gradlew tasks`, test runner runs cleanly on JVM.
  - Commit: `build: initialize multi-module gradle project structure with junit 5`

- [ ] **TASK-02**: Core Engine Foundations (TDD)
  - Route: Delegated direct (Writer trigger: 7+ core domain files and tests)
  - Details:
    - Test: Red tests for `Xoshiro256PlusPlus` PRNG matching Swift/Rust outputs.
    - Test: Red tests for `PyCompat` (roundHalfEven, floorDivide, modulo) and 31 Easing functions.
    - Test: Red tests for `Canvas`, `Frame`, `Cell`, `InputText`, and 1-based coordinate mapping `(col, row)`.
    - Implement: Port math, geometry, and frame structures to green.
  - Checks: Core JUnit 5 test suite passes 100%.
  - Commit: `feat(core): implement canvas, frame, prng, geometry, and easing curves with tests`

- [ ] **TASK-03**: Effect Batch 1 - Typographic & Terminal Reveals (6 effects) (TDD)
  - Route: Delegated direct (Writer trigger: 6 effects + tests + fixtures)
  - Effects: `print`, `decrypt`, `errorcorrect`, `randomsequence`, `laseretch`, `binarypath`.
  - Details: Port each effect with unit tests asserting against golden frames / reference step behavior.
  - Checks: All 6 reveal effect tests pass.
  - Commit: `feat(effects): port typographic and terminal reveal effects with tests`

- [ ] **TASK-04**: Effect Batch 2 - Particle, Physics & Explosive Transitions (8 effects) (TDD)
  - Route: Delegated direct (Writer trigger: 8 effects + tests + fixtures)
  - Effects: `bouncyballs`, `bubbles`, `crumble`, `fireworks`, `blackhole`, `unstable`, `spray`, `pour`.
  - Details: Implement particle mechanics, velocity/collision, and life cycle tracking with TDD.
  - Checks: All 8 physics effect tests pass.
  - Commit: `feat(effects): port particle and physics transitions with tests`

- [ ] **TASK-05**: Effect Batch 3 - Geometric, Spatial & Motion Formations (9 effects) (TDD)
  - Route: Delegated direct (Writer trigger: 9 effects + tests + fixtures)
  - Effects: `slide`, `slice`, `middleout`, `expand`, `scattered`, `swarm`, `rings`, `orbittingvolley`, `overflow`.
  - Details: Implement spatial transformations and path animations with TDD.
  - Checks: All 9 geometric effect tests pass.
  - Commit: `feat(effects): port geometric and spatial formation effects with tests`

- [ ] **TASK-06**: Effect Batch 4 - Atmospheric, Field, Scan & Glitch Sweeps (14 effects) (TDD)
  - Route: Delegated direct (Writer trigger: 14 effects + tests + fixtures)
  - Effects: `beams`, `burn`, `colorshift`, `highlight`, `matrix`, `rain`, `smoke`, `spotlights`, `sweep`, `synthgrid`, `thunderstorm`, `vhstape`, `waves`, `wipe`.
  - Details: Implement field sweep, cellular automata, and scanline effects with TDD.
  - Checks: All 14 sweep/glitch effect tests pass, full 37-effect registry verified.
  - Commit: `feat(effects): port atmospheric and glitch sweep effects with tests`

- [ ] **TASK-07**: Jetpack Compose Canvas Renderer & Glyph Atlas (`ui-compose`)
  - Route: Delegated direct (Writer trigger: Compose UI library components)
  - Details: Implement `TTFXCanvas` Composable, monospace cell text grid rendering, font metrics, color palette mapping, and frame ticker loop.
  - Checks: Compose UI render tests verify frame consumption without allocations per frame.
  - Commit: `feat(ui-compose): implement jetpack compose canvas renderer and glyph atlas`

- [ ] **TASK-08**: AGSL Post-Processing Shaders & Gallery Android Showcase App (`app`)
  - Route: Delegated direct (Writer trigger: Application activity, shaders, UI showcase)
  - Details: Implement AGSL shaders (CRT scanlines/curvature, bloom, chromatic aberration) for Android 13+ (API 33) and interactive gallery UI with effect selection and tuning sliders.
  - Checks: Application builds and passes compilation checks.
  - Commit: `feat(app): add showcase gallery app and agsl post-processing shaders`
