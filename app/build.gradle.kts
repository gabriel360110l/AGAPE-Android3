import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Backend keys live in local.properties (never committed) or in environment variables (for CI). Without them a
// debug build runs with a local development account so every screen can still be tried; a release build stops
// (see checkReleaseBackend below).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String, env: String): String = localProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(env).orEmpty()
fun quoted(value: String) = "\"$value\""
val supabaseUrl = setting("supabase.url", "AGAPE_SUPABASE_URL")
val supabaseAnonKey = setting("supabase.anonKey", "AGAPE_SUPABASE_ANON_KEY")

// Release signing. The upload key and its passwords are never committed: they come from keystore.properties
// (see keystore.properties.example) or from environment variables. Without them the release build is unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signing(name: String, env: String): String? = keystoreProps.getProperty(name)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStoreFile = signing("storeFile", "AGAPE_KEYSTORE_FILE")
val releaseStorePassword = signing("storePassword", "AGAPE_KEYSTORE_PASSWORD")
val releaseKeyAlias = signing("keyAlias", "AGAPE_KEY_ALIAS")
val releaseKeyPassword = signing("keyPassword", "AGAPE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "agape.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "agape.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "3.0.0"

        buildConfigField("String", "SUPABASE_URL", quoted(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted(supabaseAnonKey))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(setting("google.webClientId", "AGAPE_GOOGLE_WEB_CLIENT_ID")))
        // No donation page exists yet, so the donation row stays hidden while this is empty (see README "Before release").
        buildConfigField("String", "DONATION_URL", quoted(setting("donation.url", "AGAPE_DONATION_URL")))
        buildConfigField("String", "SUPPORT_EMAIL", "\"gabriel360110l@gmail.com\"")
    }

    androidResources {
        localeFilters += listOf("en", "nl")
    }

    // The language can be changed inside the app, so every App Bundle install must carry both English and Dutch.
    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// A release without the Supabase keys would fall back to the local development account, so it is stopped here
// with a clear message. For a local smoke test only, -PallowReleaseWithoutBackend=true lets it through.
val checkReleaseBackend by tasks.registering {
    val missing = listOfNotNull(
        "supabase.url (or AGAPE_SUPABASE_URL)".takeIf { supabaseUrl.isBlank() },
        "supabase.anonKey (or AGAPE_SUPABASE_ANON_KEY)".takeIf { supabaseAnonKey.isBlank() },
    )
    val allowed = providers.gradleProperty("allowReleaseWithoutBackend").orNull == "true"
    doLast {
        if (missing.isNotEmpty() && !allowed) {
            throw GradleException(
                "Release build stopped: the Supabase keys are missing: ${missing.joinToString(", ")}. " +
                    "Set them in local.properties or as environment variables. Without them the app would use a " +
                    "local development account instead of real accounts.",
            )
        }
        if (missing.isNotEmpty()) logger.warn("WARNING: release built WITHOUT Supabase keys (allowReleaseWithoutBackend); do not publish it.")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseBackend) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
