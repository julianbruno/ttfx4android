# TTFX for Android (`ttfx4android`)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-SDK%2024%20to%2035-green.svg?logo=android)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

A high-performance, native Android port of the **Terminal Text Effects (`ttfx`)** engine, built with **pure Kotlin JVM core**, **Jetpack Compose canvas rendering**, and **AGSL (Android Graphics Shading Language)** hardware-accelerated post-processing shaders.

---

## Origin & Heritage

This project is a 100% faithful Kotlin port of the Swift and Rust implementations from the original [`ttfx`](https://github.com/julianbruno/ttfx) repository, originally inspired by Chris R. Bilger's Python [`terminaltexteffects`](https://github.com/ChrisBuilds/terminaltexteffects).

### What Was Ported:
- **37 Terminal Animation Effects**: Every single effect from the Swift/Rust reference codebase is ported with mathematical parity.
- **Deterministic PRNG**: Implements `Xoshiro256PlusPlus` (64-bit seedable pseudo-random number generator) ensuring frame-by-frame reproducibility across platforms.
- **Easing Curves & Geometry**: Comprehensive implementation of cubic, exponential, sinusoidal, bounce, and elastic easing curves alongside 2D raycasting, Bézier trajectories, circular orbits, and polygon boundaries.
- **Color Interpolation & Gradients**: Full RGB/HEX color parsing, smooth gradient stepping, spectrum generators, and brightness adjustments.
- **Frame Parity Validation**: Verified with golden test suites against recorded `.frames` parity fixtures.

---

## Features

- **🚀 37 Unique Terminal Effects**: Spanning typographic reveals, particle physics, geometric motion, and atmospheric scan sweeps.
- **📱 Jetpack Compose Native**: Seamless integration with `TTFXCanvas` and `TTFXAnimationController`, providing automatic lifecycle management and 60+ FPS rendering.
- **✨ AGSL Hardware Shaders**: Post-processing effects for Android 13+ (API 33+) including **CRT Scanlines**, **Bloom Glow**, **Chromatic Aberration**, and **CRT Curvature**.
- **🎛️ Interactive Cyberpunk Showcase App**:
  - Full-screen terminal animation canvas with live preview.
  - Animation speed selector (`0.5x`, `1x`, `1.5x`, `2x`, `3x`).
  - Dynamic font size slider (`8sp` to `120sp`) with quick preset chips.
  - One-tap effect switcher across all 37 effects.
  - Interactive AGSL shader switcher.
  - Live custom display text input with instant replay.
  - Responsive, scrollable controls panel with top-bar and bottom-bar toggles.
  - Native Android 12+ SplashScreen with custom cyberpunk adaptive launcher icon.

---

## Technology Stack

| Layer | Technologies & Specifications |
| :--- | :--- |
| **Language & Toolchain** | Kotlin 2.1.10, JDK 21 (Gradle Toolchain), Gradle 8.14.5 |
| **Android Target** | Target SDK 35 (Android 15), Min SDK 24 (Android 7.0) |
| **UI Framework** | Jetpack Compose (BOM 2024.12.01), Material 3, Material Icons Extended |
| **Rendering** | Compose `Canvas`, Android `RenderEffect`, Android Graphics Shading Language (AGSL) |
| **Architecture** | Clean multi-module Gradle architecture (`:core`, `:effects`, `:ui-compose`, `:app`) |
| **Testing** | JUnit 5 (`junit-jupiter`), Kotlin Test, Deterministic Frame Parity Suites |

---

## Module Structure

```
ttfx4android/
├── core/                # Pure Kotlin JVM: Canvas, Frame, Cell, Color, Easing, PRNG (Zero Android SDK deps)
├── effects/             # 37 Effect implementations + EffectRegistry (Pure Kotlin JVM)
├── ui-compose/          # Jetpack Compose renderer (TTFXCanvas, TTFXAnimationController)
└── app/                 # Interactive Android showcase app with AGSL shaders & cyberpunk UI
```

---

## Quick Start in Jetpack Compose

Add the Compose component to your screen:

```kotlin
val effectName = "fireworks"
val text = "HELLO WORLD"
val columns = 40
val rows = 15

val controller = remember(effectName, text) {
    val canvas = Canvas(columns, rows)
    val ingested = canvas.ingest(text, anchor = "c")
    val config = EffectConfiguration(text = text, seed = 42uL)

    TTFXAnimationController(columns, rows) {
        EffectRegistry.create(effectName, config, canvas, ingested, 42uL)
            ?: error("Effect $effectName not registered")
    }
}

// Render the animation inside your Compose hierarchy
TTFXCanvas(
    controller = controller,
    modifier = Modifier
        .fillMaxWidth()
        .height(300.dp),
    fps = 30,
    fontSizeSp = 16f
)
```

---

## Documentation

- 📖 [**Installation & Setup Guide**](docs/INSTALLATION.md): Requirements, build instructions, and running tests.
- 🏛️ [**Architecture & Design**](docs/ARCHITECTURE.md): Multi-module breakdown, rendering pipeline, and AGSL shaders.
- 🎨 [**Effects Catalog**](docs/EFFECTS.md): Detailed inventory and descriptions of all 37 effects.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
