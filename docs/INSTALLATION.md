# Installation & Setup Guide

This guide details how to build, run, and test **TTFX for Android (`ttfx4android`)**, as well as how to integrate the engine into your own Android project.

---

## Prerequisites

Before building the project, ensure your environment meets the following requirements:

- **JDK**: Java Development Kit 21 or higher. (The Gradle Daemon toolchain is configured to automatically manage JDK 21).
- **Android SDK**:
  - `compileSdk`: 35 (Android 15)
  - `targetSdk`: 35
  - `minSdk`: 24 (Android 7.0 Nougat)
- **IDE**: Android Studio Ladybug (2024.2.1+), Meerkat, or IntelliJ IDEA with Android plugin.
- **Git**: Installed and configured.

---

## Cloning the Repository

Clone the repository to your local machine:

```bash
git clone git@github.com:julianbruno/ttfx4android.git
cd ttfx4android
```

---

## Building from the Command Line

The project includes the Gradle Wrapper (`gradlew`). No global Gradle installation is required.

### 1. Compile and Run Unit Tests

To run the unit test suites across all modules (`:core`, `:effects`, `:ui-compose`, `:app`):

```bash
./gradlew test
```

All 37 effects, easing curves, canvas ingestion, PRNG determinism, and frame parity tests will be executed.

### 2. Build the Debug APK

To assemble the debug application package:

```bash
./gradlew :app:assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 3. Install and Launch on an Android Device or Emulator

Ensure a device or emulator is connected via `adb devices`, then run:

```bash
./gradlew :app:installDebug
adb shell am start -n com.ttfx.app/.MainActivity
```

---

---

## Using TTFX as a Library in Other Projects

`ttfx4android` is structured as a modular library and publishes with full Maven metadata and sources JARs.

### Option A: Via JitPack (Recommended for Remote Projects)

1. Add the JitPack repository to your root `settings.gradle.kts` (inside `dependencyResolutionManagement`):
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Add the dependency in your application module's `build.gradle.kts`:
```kotlin
dependencies {
    // 1. Android Jetpack Compose UI Renderer (automatically includes :core and :effects via api dependency)
    implementation("com.github.julianbruno.ttfx4android:ui-compose:1.0.0")

    // Or if you only need the pure Kotlin JVM engine (Zero Android SDK dependencies):
    // implementation("com.github.julianbruno.ttfx4android:effects:1.0.0")
    // implementation("com.github.julianbruno.ttfx4android:core:1.0.0")
}
```

---

### Option B: Via Maven Local (`mavenLocal()`)

You can publish the artifacts locally on your workstation for offline development or local multi-project setups:

1. Inside the `ttfx4android` directory, run:
```bash
./gradlew publishToMavenLocal
```

This compiles, packages AARs/JARs with sources, and installs them into `~/.m2/repository/com/github/julianbruno/ttfx4android/`.

2. In your target Android project, enable `mavenLocal()` in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

3. Add the dependency:
```kotlin
dependencies {
    implementation("com.github.julianbruno.ttfx4android:ui-compose:1.0.0")
}
```

---

### Option C: Via Composite Build (`includeBuild`)

For local multi-repo development without installing to Maven Local:

In your target project's `settings.gradle.kts`:
```kotlin
includeBuild("../ttfx4android")
```

In your app `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.julianbruno.ttfx4android:ui-compose")
}
```

---

### Option D: Local Submodules

If using `ttfx4android` as a submodule:

In `settings.gradle.kts`:
```kotlin
include(":core")
include(":effects")
include(":ui-compose")

project(":core").projectDir = file("path/to/ttfx4android/core")
project(":effects").projectDir = file("path/to/ttfx4android/effects")
project(":ui-compose").projectDir = file("path/to/ttfx4android/ui-compose")
```

In your app module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":ui-compose")) // Transitive api dependencies automatically pull in :core and :effects

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
```

---

## Jetpack Compose Usage Example

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ttfx.core.Canvas
import com.ttfx.core.EffectConfiguration
import com.ttfx.effects.EffectRegistry
import com.ttfx.ui.compose.TTFXAnimationController
import com.ttfx.ui.compose.TTFXCanvas

@Composable
fun TerminalBanner() {
    val effectName = "matrix"
    val bannerText = "CYBERNETIC SYSTEM"
    val columns = 36
    val rows = 12

    val controller = remember(effectName, bannerText) {
        val canvas = Canvas(columns, rows)
        val ingested = canvas.ingest(bannerText, anchor = "c")
        val config = EffectConfiguration(text = bannerText, seed = 1337uL)

        TTFXAnimationController(columns, rows) {
            EffectRegistry.create(effectName, config, canvas, ingested, 1337uL)
                ?: error("Effect $effectName not registered")
        }
    }

    TTFXCanvas(
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        fps = 30,
        fontSizeSp = 18f
    )
}
```
