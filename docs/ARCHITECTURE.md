# Architecture & Technical Design

**TTFX for Android (`ttfx4android`)** is designed with a strict modular architecture separating mathematical simulation, effect logic, platform rendering, and application presentation.

```
┌─────────────────────────────────────────────────────────────┐
│                        :app Module                          │
│   Cyberpunk UI, Controls Panel, SplashScreen, AGSL Shaders │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    :ui-compose Module                       │
│    TTFXCanvas (Monospace Glyph Rendering & Cell Layout)     │
│    TTFXAnimationController (Frame State & Delta Timing)    │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      :effects Module                        │
│   37 Concrete Effect Engines & EffectRegistry Factory       │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       :core Module                          │
│   Canvas, Frame, Cell, Easing, Color, Xoshiro256PlusPlus     │
│   (Pure Kotlin JVM - 100% Deterministic & Portable)         │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. Module Breakdown

### `:core` Module (Pure Kotlin JVM)
The `:core` module contains zero Android SDK dependencies. It compiles directly to Kotlin JVM bytecode and can run on any JVM runtime, desktop, or server environment.

- **`Canvas` & `Frame`**: The 2D matrix coordinate system of character cells. Supports text ingestion with directional alignment anchors (`c`, `nw`, `se`, etc.).
- **`Cell` & `Coordinate`**: Atomic cell representation holding character symbol, foreground/background RGB colors, and 2D spatial coordinate `(x, y)`.
- **`Color` & `Gradient`**: Color representation with RGB hexadecimal parsing, linear interpolation (`lerp`), step gradients, and brightness scaling.
- **`Easing`**: 30+ mathematical easing functions (Linear, In/Out Quad, Cubic, Quart, Quint, Sine, Expo, Circ, Elastic, Back, Bounce).
- **`Xoshiro256PlusPlus`**: 64-bit high-performance, seedable pseudo-random number generator ensuring identical execution across Android, Swift, Rust, and Python reference implementations.
- **`AnsiFrameRenderer`**: Terminal string renderer formatting frames with ANSI 24-bit truecolor escape codes for terminal testing and golden test comparisons.

---

### `:effects` Module (Pure Kotlin JVM)
Contains the algorithmic state machines for all 37 distinct terminal effects:

- **Common Interface**: Every effect implements `com.ttfx.core.Effect`:
  ```kotlin
  interface Effect {
      fun tick(): TickStatus
      fun render(frame: Frame)
  }
  ```
- **Lifecycle & Execution**:
  - Each tick updates internal physics, trajectories, particle states, or scanlines.
  - Returns `TickStatus.InFlight` while active, or `TickStatus.Complete` once all cells reach their final resting state.
- **`EffectRegistry`**: Factory pattern mapping lowercase effect identifiers (`"fireworks"`, `"matrix"`, `"decrypt"`, etc.) to concrete constructors.

---

### `:ui-compose` Module (Android & Jetpack Compose)
Bridges the pure Kotlin simulation engine into the Android UI hierarchy:

- **`TTFXAnimationController`**:
  - Manages the animation coroutine loop via delta timing.
  - Exposes `frameCount: StateFlow<Int>` and `tickStatus: StateFlow<TickStatus>`.
  - Supports pause, resume, reset, and step execution.
- **`TTFXCanvas`**:
  - Custom Jetpack Compose `Canvas` element.
  - **Dynamic Scaling & Aspect Ratio**: Measures the exact pixel bounds and monospace char width/height to dynamically center and scale the terminal grid.
  - **Monospace Text Rendering**: Draws characters using Android `NativeCanvas.drawText()` with custom `Paint` caching, applying cell-specific foreground RGB colors and background fills.
  - Respects exact dynamic font size in SP (`fontSizeSp`).

---

### `:app` Module (Android Application)
The presentation layer providing a full terminal showcase experience:

- **`ShowcaseScreen`**:
  - Interactive top bar with real-time frame counter badge `F: 128 [INFLIGHT]` and controls toggle.
  - Full-screen terminal viewport.
  - Responsive collapsible controls sheet with live speed multiplier (`0.5x` - `3x`), font size slider (`8sp` - `120sp`), preset chips, effect picker, and text input.
  - Android system bars insets handling (`statusBarsPadding`, `navigationBarsPadding`).
- **`SplashScreen`**:
  - Hardware-accelerated boot sequence using `decrypt` animation on `TTFXCanvas`.
  - Modern `core-splashscreen` API integration with custom cyberpunk adaptive vector launcher icon.
- **AGSL Shaders (`ShaderEffectMode`)**:
  - Real-time hardware-accelerated post-processing using Android 13+ `RuntimeShader` and `RenderEffect`.
  - Shaders include:
    1. `CRT_SCANLINES`: Horizontal scanlines with phosphor flicker and vignette.
    2. `BLOOM_GLOW`: Gaussian-approximated neon bloom emphasizing bright text characters.
    3. `CHROMATIC_ABERRATION`: Color channel offset simulating retro RGB CRT separation.
    4. `CRT_CURVATURE`: Spherical barrel distortion matching retro curved monitors.
  - Graceful fallback to `NONE` on pre-API 33 devices or software rendering.

---

## 2. Determinism & Testability

Every animation in `ttfx4android` is deterministic when initialized with the same random seed:

1. **Seed Reproducibility**: Given `seed = 42uL`, an effect generates identical cell trajectories on every run.
2. **Golden Testing**: The test suite compares rendered ANSI frames against `.frames` fixtures generated from the Swift oracle to guarantee parity.
3. **Headless Execution**: Since `:core` and `:effects` require no Android dependencies or mocks, hundreds of animation ticks run in sub-second JUnit 5 test runs.
