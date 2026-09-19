# Using TTFX as a Library

This guide provides step-by-step instructions for integrating **TTFX for Android (`ttfx4android`)** into your own Android or Kotlin JVM projects.

---

## 📦 Available Artifacts

The library is published with group `com.github.julianbruno.ttfx4android` and version `1.0.0`:

| Artifact ID | Platform | Description |
| :--- | :--- | :--- |
| **`ui-compose`** | Android (AAR) | Complete Jetpack Compose canvas renderer (`TTFXCanvas`, `TTFXAnimationController`). Transitive `api` dependencies automatically pull in `:core` and `:effects`. |
| **`effects`** | Pure JVM (JAR) | All 37 terminal effect state machines & `EffectRegistry`. Pulls in `:core`. Zero Android SDK dependencies. |
| **`core`** | Pure JVM (JAR) | Core data models (`Canvas`, `Frame`, `Cell`, `Color`, `Easing`, `Xoshiro256PlusPlus`). Zero Android SDK dependencies. |

---

## 🚀 Integration Methods

### Option 1: Remote Integration via JitPack (Recommended)

JitPack builds and serves the library directly from GitHub releases and tags.

#### Step 1: Add JitPack Repository
In your target project's `settings.gradle.kts`:

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

#### Step 2: Add the Dependency
In your application's `app/build.gradle.kts`:

```kotlin
dependencies {
    // Jetpack Compose Canvas renderer + all 37 effects + core
    implementation("com.github.julianbruno.ttfx4android:ui-compose:1.0.0")

    // Or if you only need the pure Kotlin JVM animation engine (e.g. backend / desktop / unit testing):
    // implementation("com.github.julianbruno.ttfx4android:effects:1.0.0")
    // implementation("com.github.julianbruno.ttfx4android:core:1.0.0")
}
```

---

### Option 2: Local Publishing via Maven Local (`mavenLocal()`)

Useful for local development or testing modifications across multiple local repositories.

#### Step 1: Publish Artifacts to Maven Local
Inside the `ttfx4android` repository, run:

```bash
./gradlew publishToMavenLocal
```

This compiles, packages the `.aar` and `.jar` artifacts with sources, and installs them into your local workstation repository at `~/.m2/repository/com/github/julianbruno/ttfx4android/`.

#### Step 2: Consume from Your Other Project
In your other Android project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

In your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.julianbruno.ttfx4android:ui-compose:1.0.0")
}
```

---

### Option 3: Composite Build (`includeBuild`)

Allows live editing of `ttfx4android` source code directly within another Android project without publishing artifacts.

In your other project's `settings.gradle.kts`:

```kotlin
includeBuild("path/to/ttfx4android")
```

In your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.julianbruno.ttfx4android:ui-compose")
}
```

---

## 💻 Jetpack Compose Quick Start

### Basic Usage

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
fun CyberpunkHeader() {
    val effectName = "matrix"
    val text = "ACCESS GRANTED"
    val columns = 40
    val rows = 12

    val controller = remember(effectName, text) {
        val canvas = Canvas(columns, rows)
        val ingested = canvas.ingest(text, anchor = "c")
        val config = EffectConfiguration(text = text, seed = 42uL)

        TTFXAnimationController(columns, rows) {
            EffectRegistry.create(effectName, config, canvas, ingested, 42uL)
                ?: error("Effect $effectName not registered")
        }
    }

    TTFXCanvas(
        controller = controller,
        modifier = Modifier.fillMaxSize(),
        fps = 30,
        fontSizeSp = 16f
    )
}
```

---

## 🛡️ Proguard / R8 Rules

The `ui-compose` AAR includes automatic consumer Proguard rules (`consumer-rules.pro`). If you are consuming only `:core` or `:effects` in a minified release build, ensure reflection and model classes are kept:

```proguard
-keep class com.ttfx.core.** { *; }
-keep class com.ttfx.effects.** { *; }
-keep class com.ttfx.ui.compose.** { *; }
```
