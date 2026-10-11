plugins {
    // Kotlin-компиляция встроена в AGP 9 (built-in Kotlin): отдельный плагин не нужен.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Подпись релиза: ключи приходят из CI/локального окружения, в репозитории их нет.
// Ожидаемые имена: AMPERGRAD_KEYSTORE (путь), AMPERGRAD_KEYSTORE_PASSWORD,
// AMPERGRAD_KEY_ALIAS, AMPERGRAD_KEY_PASSWORD.
val keystorePath: String? = System.getenv("AMPERGRAD_KEYSTORE")

android {
    namespace = "com.ampergrad.game"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ampergrad.game"
        minSdk = 26
        targetSdk = 36 // поведение Android 16; compileSdk 37 нужен библиотекам Compose 2026.09 и MWA 2.2.0
        versionCode = 1
        versionName = "0.1.0"
        // Адрес сервера клейма. Переопределяется: ./gradlew -PserverUrl=https://...
        val serverUrl = (project.findProperty("serverUrl") as String?) ?: "https://api.ampergrad.example"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "ENGINE_VERSION", "\"0.1.0\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("AMPERGRAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AMPERGRAD_KEY_ALIAS")
                keyPassword = System.getenv("AMPERGRAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // включаем R8 после первой закрытой беты
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Кошелёк: Solana Mobile Wallet Adapter (Phantom, Seed Vault, Solflare, Backpack — все MWA-кошельки)
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.2.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303") // реальная реализация org.json для JUnit (в android.jar — заглушки)
}
