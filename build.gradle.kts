plugins {
    id("com.android.application") version "9.4.1" apply false
    // Pins the Kotlin compiler used by AGP's built-in Kotlin support (GeckoView 157 is built with Kotlin 2.4).
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
