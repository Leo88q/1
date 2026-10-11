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

// Контент (лор, реестр) генерируется в репозитории командой `npm run content`.
// Если файл есть — копируем его в assets приложения и в ресурсы unit-тестов.
// В CI файл подкладывает workflow, локально достаточно выполнить npm run content.
val loreSource = java.io.File(rootProject.projectDir.parentFile, "packages/engine/content/lore.json")
val loreTargets = listOf(
    java.io.File(project.projectDir, "src/main/assets/lore.json"),
    java.io.File(project.projectDir, "src/test/resources/lore.json"),
)

val syncContent by tasks.registering {
    group = "ampergrad"
    description = "Копирует контент (lore.json) в assets и тестовые ресурсы"
    inputs.files(loreSource).optional()
    outputs.files(loreTargets)
    doLast {
        if (!loreSource.exists()) {
            logger.lifecycle("lore.json не найден — шаг пропущен (выполните npm run content в корне репозитория)")
            return@doLast
        }
        loreTargets.forEach { target ->
            target.parentFile.mkdirs()
            loreSource.copyTo(target, overwrite = true)
        }
    }
}

tasks.named("preBuild") { dependsOn(syncContent) }
tasks.matching { it.name.contains("UnitTest") && it.name.startsWith("compile") }
    .configureEach { dependsOn(syncContent) }

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

    // Тесты: JUnit4 — стандарт для unit-тестов Android; kotlin-test добавляет ассерты.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation("org.json:json:20240303") // реальная реализация org.json для JUnit (в android.jar — заглушки)
}
