import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.pyoungws.bluewolfplayer.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // Also runs commonTest against the Android variant of the dependencies on the host.
        withHostTest {}
    }

    // Plain JVM target so shared business logic is unit tested with JUnit on the host.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.jellyfin.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.nextlib.media3ext)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// KMP has no `test` task; add one so the standard `./gradlew test` runs the shared tests.
tasks.register("test") {
    group = "verification"
    description = "Runs the shared unit tests on the JVM and Android host targets."
    dependsOn("jvmTest", "testAndroidHostTest")
}

// The SDK logs via kotlin-logging, which defaults to SLF4J (not on the classpath); use java.util.logging.
tasks.named<Test>("jvmTest") {
    systemProperty("kotlin-logging-to-jul", "true")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
