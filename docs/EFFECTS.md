# Effects Catalog & Visual Parity

**TTFX for Android (`ttfx4android`)** includes all **37 distinct terminal effects** ported with 100% mathematical parity from the original Swift (Metal) and Rust reference implementations in [`julianbruno/ttfx`](https://github.com/julianbruno/ttfx).

---

## Featured Parity: Android (Compose + AGSL) vs. Swift (Metal)

The following side-by-side comparisons demonstrate the identical easing curves, particle trajectories, and physics models between the native Android Compose port and the original Swift Metal implementation:

### `fireworks` Effect
| Android (Jetpack Compose + AGSL CRT) | Original Swift (Apple Metal) |
| :---: | :---: |
| <img src="media/android_fireworks.gif" alt="Android Fireworks" width="300" /> | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/fireworks.gif" alt="Swift Metal Fireworks" width="300" /> |
| [Download Android MP4](media/android_fireworks.mp4) | [Download Swift MP4](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/fireworks.mp4) |

### `decrypt` Effect
| Android (Compose SplashScreen + Bloom) | Original Swift (Apple Metal) |
| :---: | :---: |
| <img src="media/android_decrypt.gif" alt="Android Decrypt" width="300" /> | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/decrypt.gif" alt="Swift Metal Decrypt" width="300" /> |
| [Download Android MP4](media/android_splash.mp4) | [Download Swift MP4](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/decrypt.mp4) |

---

## Complete Effect Inventory (All 37 Effects)

### 1. Typographic & Reveal Effects (6 Effects)
Effects focusing on character decrypting, typing cadence, and cryptographic glyph manipulation.

| Effect Name / Key | Visual Preview (Swift Metal Reference) | Video Comparison | Description & Android Parity |
| :--- | :---: | :---: | :--- |
| **Binary Path**<br>`binarypath` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/binarypath.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/binarypath.mp4) | Characters travel along binary sequence conduits across the screen before settling into place. Pure Kotlin simulation. |
| **Decrypt**<br>`decrypt` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/decrypt.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/decrypt.mp4) | Unscrambles text by cycling through random glyphs with decrypting animation until resolving into final characters. Used in Android `SplashScreen`. |
| **Error Correct**<br>`errorcorrect` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/errorcorrect.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/errorcorrect.mp4) | Simulates noisy transmission data where characters glitch and get iteratively corrected by parity algorithms. |
| **Laser Etch**<br>`laseretch` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/laseretch.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/laseretch.mp4) | A focused high-energy laser sweeps horizontally, vaporizing and burning the text onto the canvas with ember cool-down. |
| **Print**<br>`print` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/print.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/print.mp4) | Emulates retro teleprinter and mechanical typewriter printhead mechanics with carriage sound cadence. |
| **Random Sequence**<br>`randomsequence` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/randomsequence.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/randomsequence.mp4) | Reveals characters in pseudo-random order across the text block with subtle color fade and alpha stepping. |

---

### 2. Particle, Explosive & Physics Transitions (8 Effects)
Effects simulating gravity, orbital mechanics, fluid dynamics, and explosive particle dispersal.

| Effect Name / Key | Visual Preview (Swift Metal Reference) | Video Comparison | Description & Android Parity |
| :--- | :---: | :---: | :--- |
| **Black Hole**<br>`blackhole` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/blackhole.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/blackhole.mp4) | Text particles are sucked into a gravitational singularity before bursting outward back to resting positions. |
| **Bouncy Balls**<br>`bouncyballs` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/bouncyballs.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/bouncyballs.mp4) | Characters collapse into bouncing elastic spheres obeying gravitational acceleration and coefficient of restitution. |
| **Bubbles**<br>`bubbles` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/bubbles.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/bubbles.mp4) | Fluid bubble particles float upward from the bottom of the screen, popping to deposit text characters. |
| **Crumble**<br>`crumble` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/crumble.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/crumble.mp4) | Text structure weakens and crumbles downward into a heap of falling dust before rebuilding with elastic snap. |
| **Fireworks**<br>`fireworks` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/fireworks.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/fireworks.mp4) | Rockets launch vertically into the sky, bursting into colorful explosive sparks that resolve into the text. |
| **Pour**<br>`pour` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/pour.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/pour.mp4) | Characters pour like liquid from a designated nozzle or corner, splashing into place with damping physics. |
| **Spray**<br>`spray` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/spray.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/spray.mp4) | An aerosol spray nozzle sweeps across the canvas, atomizing droplets that condense into characters. |
| **Unstable**<br>`unstable` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/unstable.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/unstable.mp4) | Radioactive jittering where characters vibrate with increasing amplitude before settling into stable state. |

---

### 3. Geometric, Spatial & Motion Formations (9 Effects)
Effects utilizing 2D spatial transformations, slicing planes, swarm coordination, and orbital paths.

| Effect Name / Key | Visual Preview (Swift Metal Reference) | Video Comparison | Description & Android Parity |
| :--- | :---: | :---: | :--- |
| **Expand**<br>`expand` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/expand.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/expand.mp4) | Text expands outward from a dense central cluster into full layout dimensions with cubic easing. |
| **Middle Out**<br>`middleout` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/middleout.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/middleout.mp4) | Characters unfurl from the horizontal and vertical centerlines outward like an expanding iris. |
| **Orbitting Volley**<br>`orbittingvolley` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/orbittingvolley.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/orbittingvolley.mp4) | Particle groups orbit around the canvas in ellipses before slingshotting into their destination slots. |
| **Overflow**<br>`overflow` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/overflow.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/overflow.mp4) | Characters cascade beyond the canvas boundary and wrap back around in a continuous flowing stream. |
| **Rings**<br>`rings` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/rings.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/rings.mp4) | Characters rotate in concentric circular rings at varying angular velocities before locking into place. |
| **Scattered**<br>`scattered` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/scattered.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/scattered.mp4) | Characters start scattered randomly across the entire screen and snap simultaneously to their coordinates. |
| **Slice**<br>`slice` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/slice.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/slice.mp4) | The canvas is partitioned into diagonal or horizontal slice planes that slide in opposing directions. |
| **Slide**<br>`slide` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/slide.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/slide.mp4) | Coordinated row-by-row or column-by-column sliding animation with directional easing curves. |
| **Swarm**<br>`swarm` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/swarm.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/swarm.mp4) | Flocking behavior where character particles fly in coordinated boids swarms before landing. |

---

### 4. Atmospheric, Field, Scan & Glitch Sweeps (14 Effects)
Atmospheric phenomena, scanlines, weather simulations, and analog signal degradation.

| Effect Name / Key | Visual Preview (Swift Metal Reference) | Video Comparison | Description & Android Parity |
| :--- | :---: | :---: | :--- |
| **Beams**<br>`beams` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/beams.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/beams.mp4) | High-intensity luminous laser beams sweep across the canvas illuminating characters in their wake. |
| **Burn**<br>`burn` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/burn.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/burn.mp4) | Fiery thermal fronts scorch across the canvas with ember glow and smoke cooling gradients. |
| **Color Shift**<br>`colorshift` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/colorshift.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/colorshift.mp4) | Chromatic waves oscillate across the RGB spectrum in undulating color gradients. |
| **Highlight**<br>`highlight` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/highlight.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/highlight.mp4) | A luminous highlight bar sweeps across the text line, creating a reflective sheen. |
| **Matrix**<br>`matrix` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/matrix.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/matrix.mp4) | Iconic falling digital rain code streams with glowing green head characters and phosphor trails. |
| **Rain**<br>`rain` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/rain.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/rain.mp4) | Atmospheric rain drops fall diagonally or vertically across the screen, revealing splashed letters. |
| **Smoke**<br>`smoke` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/smoke.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/smoke.mp4) | Bilinear turbulent smoke plumes drift across the screen, obscuring and clarifying characters. |
| **Spotlights**<br>`spotlights` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/spotlights.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/spotlights.mp4) | Moving cone spotlights sweep in darkness, illuminating letters caught in the focal beam. |
| **Sweep**<br>`sweep` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/sweep.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/sweep.mp4) | A clean linear radar or laser sweep across the canvas with progressive reveal. |
| **Synth Grid**<br>`synthgrid` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/synthgrid.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/synthgrid.mp4) | Retro 80s outrun synthwave perspective grid with animated horizon scanlines. |
| **Thunderstorm**<br>`thunderstorm` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/thunderstorm.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/thunderstorm.mp4) | Heavy rain punctuated by sudden blinding lightning flashes that illuminate the canvas. |
| **VHS Tape**<br>`vhstape` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/vhstape.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/vhstape.mp4) | Analog videotape tracking distortion, static jitter, scanline noise, and tape roll artifacts. |
| **Waves**<br>`waves` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/waves.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/waves.mp4) | Harmonic sine wave ripples undulating across rows and columns of text. |
| **Wipe**<br>`wipe` | <img src="https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/effects/wipe.gif" width="180" /> | [MP4 Video](https://raw.githubusercontent.com/julianbruno/ttfx/main/docs/swift-port/comparisons/wipe.mp4) | Directional curtain wipe with smooth gradient edge transition. |

---

## Instantiating Effects in Android

```kotlin
import com.ttfx.core.Canvas
import com.ttfx.core.EffectConfiguration
import com.ttfx.effects.EffectRegistry
import com.ttfx.ui.compose.TTFXAnimationController
import com.ttfx.ui.compose.TTFXCanvas

// List all 37 effect keys
val allAvailableEffects: List<String> = EffectRegistry.allEffects

// Create an animation controller
val canvas = Canvas(columns = 40, rows = 15)
val ingested = canvas.ingest("HELLO ANDROID", anchor = "c")
val config = EffectConfiguration(text = "HELLO ANDROID", seed = 42uL)

val controller = TTFXAnimationController(40, 15) {
    EffectRegistry.create("matrix", config, canvas, ingested, 42uL)!!
}
```
