plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val distributionVersion = providers.gradleProperty("releaseVersionName").orElse(providers.gradleProperty("appVersion")).get()
val versionParts = distributionVersion.split('.').map { it.toInt() }
val distributionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1000 + versionParts[2]
android {
    namespace = "com.karaokelink"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    val releaseKey = System.getenv("KARAOKELINK_KEYSTORE_PATH")
    if (!releaseKey.isNullOrBlank()) {
        val distribution = signingConfigs.create("distribution") {
            storeFile = file(releaseKey)
            storePassword = System.getenv("KARAOKELINK_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KARAOKELINK_KEY_ALIAS")
            keyPassword = System.getenv("KARAOKELINK_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = distribution
    }
    defaultConfig {
        applicationId = "com.karaokelink"
        minSdk = 28
        targetSdk = 35
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt() ?: distributionCode
        versionName = distributionVersion
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared"; cppFlags += listOf("-std=c++17", "-Wall", "-Wextra") } }
    }
    buildFeatures { prefab = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.oboe:oboe:1.9.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
}