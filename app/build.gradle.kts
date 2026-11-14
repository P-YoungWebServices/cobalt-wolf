import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is opt-in: `keystore.properties` in the repo root is
// gitignored and holds the keystore path + passwords. Without it the release
// build still compiles but produces an unsigned APK.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "com.pyoungws.cobaltwolf"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pyoungws.cobaltwolf"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Dev builds only: bake the local dev credentials into BuildConfig so
            // DevSeed can provision a fresh install. The file is gitignored; nothing
            // is ever written back or committed.
            val credFile = rootProject.file("dev.credentials.properties")
            val creds = Properties()
            if (credFile.exists()) credFile.inputStream().use { creds.load(it) }
            fun cred(key: String): String =
                creds.getProperty(key, "").replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "DEV_SERVER", "\"${cred("server")}\"")
            buildConfigField("String", "DEV_USERNAME", "\"${cred("username")}\"")
            buildConfigField("String", "DEV_PASSWORD", "\"${cred("password")}\"")
            buildConfigField("String", "DEV_SEERR_SERVER", "\"${cred("seerr.server")}\"")
            buildConfigField("String", "DEV_RADARR_URL", "\"${cred("radarr.url")}\"")
            buildConfigField("String", "DEV_RADARR_APIKEY", "\"${cred("radarr.apikey")}\"")
            buildConfigField("String", "DEV_SONARR_URL", "\"${cred("sonarr.url")}\"")
            buildConfigField("String", "DEV_SONARR_APIKEY", "\"${cred("sonarr.apikey")}\"")
            buildConfigField("String", "DEV_QBITTORRENT_URL", "\"${cred("qbittorrent.url")}\"")
            buildConfigField("String", "DEV_QBITTORRENT_USERNAME", "\"${cred("qbittorrent.username")}\"")
            buildConfigField("String", "DEV_QBITTORRENT_PASSWORD", "\"${cred("qbittorrent.password")}\"")
            buildConfigField("String", "DEV_BAZARR_URL", "\"${cred("bazarr.url")}\"")
            buildConfigField("String", "DEV_BAZARR_APIKEY", "\"${cred("bazarr.apikey")}\"")
            buildConfigField("String", "DEV_PROWLARR_URL", "\"${cred("prowlarr.url")}\"")
            buildConfigField("String", "DEV_PROWLARR_APIKEY", "\"${cred("prowlarr.apikey")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // The Jellyfin SDK exposes java.time (e.g. SyncPlay message timestamps); API 24-25 lacks it.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.nextlib.media3ext)
    implementation(libs.ass.media)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.glance.appwidget)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
