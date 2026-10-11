// Корневой проект Android-приложения АМПЕРГРАД.
// Версии подобраны по факту требований зависимостей:
// MWA 2.2.0 собран с kotlin-stdlib 2.4.10 (берём Kotlin 2.4.21),
// а Compose BOM 2026.09 и MWA требуют compileSdk 37 и AGP ≥ 9.1.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
