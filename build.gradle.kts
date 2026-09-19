plugins {
    kotlin("jvm") version "2.1.10" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
}

allprojects {
    group = "com.github.julianbruno.ttfx4android"
    version = "1.0.0"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
