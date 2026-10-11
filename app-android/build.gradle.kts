// Корневой проект Android-приложения АМПЕРГРАД.
//
// Версии подобраны по фактическим требованиям зависимостей:
//  • MWA 2.2.0 собран с kotlin-stdlib 2.4.10 → KGP поднимаем до 2.4.21;
//  • Compose BOM 2026.09 и MWA 2.2.0 требуют compileSdk 37 и AGP ≥ 9.1;
//  • начиная с AGP 9.0 поддержка Kotlin встроена: отдельный плагин
//    org.jetbrains.kotlin.android применять нельзя.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 поставляет KGP 2.2.10; этой версии мало для библиотек, собранных Kotlin 2.4.x.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
