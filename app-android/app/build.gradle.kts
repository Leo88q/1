plugins {
    // Kotlin-компиляция встроена в AGP 9 (built-in Kotlin): отдельный плагин не нужен.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

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

// Контент (лор, реестр, магазин) генерируется командой `npm run content`, тест-векторы
// ядра — командой `npm run vectors`. Оба файла лежат в репозитории, поэтому свежий клон
// собирается даже без Node: копируем то, что есть, и пропускаем отсутствующее.
val contentDir = File(rootProject.projectDir.parentFile, "packages/engine/content")
val vectorsFile = File(rootProject.projectDir.parentFile, "packages/engine/vectors/levels.json")

/** Файл в репозитории → куда его положить внутри модуля приложения. */
val syncedFiles = mapOf(
    File(contentDir, "lore.json") to listOf(
        File(project.projectDir, "src/main/assets/lore.json"),
        File(project.projectDir, "src/test/resources/lore.json"),
    ),
    File(contentDir, "cosmetics.json") to listOf(
        File(project.projectDir, "src/main/assets/cosmetics.json"),
        File(project.projectDir, "src/test/resources/cosmetics.json"),
    ),
    vectorsFile to listOf(File(project.projectDir, "src/test/resources/levels.json")),
)

val syncContent = tasks.register("syncContent") {
    group = "ampergrad"
    description = "Копирует контент и тест-векторы ядра в assets и ресурсы unit-тестов"
    inputs.files(syncedFiles.keys.toList()).optional()
    outputs.files(syncedFiles.values.flatten())
    doLast {
        val missing = syncedFiles.keys.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            logger.lifecycle(
                "шаг пропущен: нет файлов $missing — выполните npm run content и npm run vectors в корне репозитория",
            )
        }
        syncedFiles.forEach { (source, targets) ->
            if (!source.exists()) return@forEach
            targets.forEach { target ->
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
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
