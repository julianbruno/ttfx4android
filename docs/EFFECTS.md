# Effects Catalog

**TTFX for Android (`ttfx4android`)** includes all 37 distinct terminal effects ported from the Swift/Rust reference implementations.

Every effect is registered in `EffectRegistry` and can be instantiated by name.

---

## Complete Effect Inventory (37 Effects)

### 1. Typographic & Reveal Effects (6 Effects)
Effects focusing on character decrypting, typing cadence, and cryptographic glyph manipulation.

| Effect Name | Key | Description |
| :--- | :--- | :--- |
| **Binary Path** | `binarypath` | Characters travel along binary sequence conduits across the screen before settling into place. |
| **Decrypt** | `decrypt` | Unscrambles text by cycling through random glyphs with decrypting animation until resolving into final characters. |
| **Error Correct** | `errorcorrect` | Simulates noisy transmission data where characters glitch and get iteratively corrected by parity algorithms. |
| **Laser Etch** | `laseretch` | A focused high-energy laser sweeps horizontally, vaporizing and burning the text onto the canvas. |
| **Print** | `print` | Emulates retro teleprinter and mechanical typewriter printhead mechanics with carriage sound cadence. |
| **Random Sequence**| `randomsequence`| Reveals characters in pseudo-random order across the text block with subtle color fade. |

---

### 2. Particle, Explosive & Physics Transitions (8 Effects)
Effects simulating gravity, orbital mechanics, fluid dynamics, and explosive particle dispersal.

| Effect Name | Key | Description |
| :--- | :--- | :--- |
| **Black Hole** | `blackhole` | Text particles are sucked into a gravitational singularity before bursting outward back to resting positions. |
| **Bouncy Balls** | `bouncyballs` | Characters collapse into bouncing elastic spheres obeying gravitational acceleration and restitution. |
| **Bubbles** | `bubbles` | Fluid bubble particles float upward from the bottom of the screen, popping to deposit text characters. |
| **Crumble** | `crumble` | Text structure weakens and crumbles downward into a heap of falling dust before rebuilding. |
| **Fireworks** | `fireworks` | Rockets launch vertically into the sky, bursting into colorful explosive sparks that resolve into the text. |
| **Pour** | `pour` | Characters pour like liquid from a designated nozzle or corner, splashing into place. |
| **Spray** | `spray` | An aerosol spray nozzle sweeps across the canvas, atomizing droplets that condense into characters. |
| **Unstable** | `unstable` | Radioactive jittering where characters vibrate with increasing amplitude before settling into stable state. |

---

### 3. Geometric, Spatial & Motion Formations (9 Effects)
Effects utilizing 2D spatial transformations, slicing planes, swarm coordination, and orbital paths.

| Effect Name | Key | Description |
| :--- | :--- | :--- |
| **Expand** | `expand` | Text expands outward from a dense central cluster into full layout dimensions. |
| **Middle Out** | `middleout` | Characters unfurl from the horizontal and vertical centerlines outward like an expanding iris. |
| **Orbitting Volley**| `orbittingvolley`| Particle groups orbit around the canvas in ellipses before slingshotting into their destination slots. |
| **Overflow** | `overflow` | Characters cascade beyond the canvas boundary and wrap back around in a continuous flowing stream. |
| **Rings** | `rings` | Characters rotate in concentric circular rings at varying angular velocities before locking into place. |
| **Scattered** | `scattered` | Characters start scattered randomly across the entire screen and snap simultaneously to their coordinates. |
| **Slice** | `slice` | The canvas is partitioned into diagonal or horizontal slice planes that slide in opposing directions. |
| **Slide** | `slide` | Coordinated row-by-row or column-by-column sliding animation with directional easing. |
| **Swarm** | `swarm` | Flocking behavior where character particles fly in coordinated boids swarms before landing. |

---

### 4. Atmospheric, Field, Scan & Glitch Sweeps (14 Effects)
Atmospheric phenomena, scanlines, weather simulations, and analog signal degradation.

| Effect Name | Key | Description |
| :--- | :--- | :--- |
| **Beams** | `beams` | High-intensity luminous laser beams sweep across the canvas illuminating characters in their wake. |
| **Burn** | `burn` | Fiery thermal fronts scorch across the canvas with ember glow and smoke cooling. |
| **Color Shift** | `colorshift` | Chromatic waves oscillate across the RGB spectrum in undulating color gradients. |
| **Highlight** | `highlight` | A luminous highlight bar sweeps across the text line, creating a reflective sheen. |
| **Matrix** | `matrix` | Iconic falling digital rain code streams with glowing green head characters and phosphor trails. |
| **Rain** | `rain` | Atmospheric rain drops fall diagonally or vertically across the screen, revealing splashed letters. |
| **Smoke** | `smoke` | Bilinear turbulent smoke plumes drift across the screen, obscuring and clarifying characters. |
| **Spotlights** | `spotlights` | Moving cone spotlights sweep in darkness, illuminating letters caught in the focal beam. |
| **Sweep** | `sweep` | A clean linear radar or laser sweep across the canvas with progressive reveal. |
| **Synth Grid** | `synthgrid` | Retro 80s outrun synthwave perspective grid with animated horizon scanlines. |
| **Thunderstorm** | `thunderstorm` | Heavy rain punctuated by sudden blinding lightning flashes that illuminate the canvas. |
| **VHS Tape** | `vhstape` | Analog videotape tracking distortion, static jitter, scanline noise, and tape roll artifacts. |
| **Waves** | `waves` | Harmonic sine wave ripples undulating across rows and columns of text. |
| **Wipe** | `wipe` | Directional curtain wipe with smooth gradient edge transition. |

---

## Instantiation via `EffectRegistry`

All effects are accessible uniformly:

```kotlin
import com.ttfx.effects.EffectRegistry

// List all 37 effect identifiers
val effects: List<String> = EffectRegistry.allEffects

// Create an effect dynamically
val effect = EffectRegistry.create(
    name = "fireworks",
    configuration = config,
    canvas = canvas,
    inputText = ingestedText,
    seed = 42uL
)
```
