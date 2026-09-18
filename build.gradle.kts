plugins {
    kotlin("jvm") version "2.1.10" apply false
    id("com.android.library") version "8.8.0" apply false
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
