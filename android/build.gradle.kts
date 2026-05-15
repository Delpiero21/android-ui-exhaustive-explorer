// Top-level build file — applies plugins to all submodules.
plugins {
    // Android plugins
    id("com.android.application") version "8.7.3" apply false
    // Kotlin
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
