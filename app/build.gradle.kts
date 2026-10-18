import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Release signing: VOLT_* env vars (CI) > keystore.properties (local) > unsigned.
fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: ksProps.getProperty(prop)

val storeFilePath = signingValue("VOLT_KEYSTORE_FILE", "storeFile")
val hasSigning = storeFilePath != null &&
    signingValue("VOLT_KEYSTORE_PASSWORD", "storePassword") != null &&
    signingValue("VOLT_KEY_ALIAS", "alias") != null &&
    signingValue("VOLT_KEY_PASSWORD", "keyPassword") != null

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.kobiregev.volt"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.kobiregev.volt"
        minSdk = 28
        targetSdk = 34
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"

        // Weather location comes from the untracked local.properties, so no home city is committed.
        // Empty values turn the weather fetch off.
        buildConfigField("String", "WEATHER_CITY", "\"${localProps.getProperty("weather.city", "")}\"")
        buildConfigField("String", "WEATHER_LAT", "\"${localProps.getProperty("weather.lat", "")}\"")
        buildConfigField("String", "WEATHER_LON", "\"${localProps.getProperty("weather.lon", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = signingValue("VOLT_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("VOLT_KEY_ALIAS", "alias")
                keyPassword = signingValue("VOLT_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

ktlint {
    version.set("1.3.1")
    android.set(true)
}
