import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Credentiale Supabase — local.properties e gitignored, niciodata in cod/VCS.
// Vezi docs/user-management-plan.md pentru cum se obtin (Project Settings -> API).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun requiredLocalProperty(key: String): String =
    localProperties.getProperty(key)
        ?: throw GradleException(
            "Lipseste \"$key\" din local.properties. Adauga-l (vezi docs/user-management-plan.md) " +
                "inainte de a compila — necesar pentru SDK-ul Supabase."
        )

android {
    namespace = "com.pillpronto"
    // 36, nu 35: androidx.browser (adus tranzitiv de auth-kt, pentru flow-uri OAuth cu Custom
    // Tabs — relevant la Google Sign-In, Faza 1.5b urmatoare) cere compileSdk >= 36.
    // targetSdk ramane 35 — nu schimbam comportament la runtime, doar API-urile de compilare.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pillpronto"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // HiltTestRunner instantiaza HiltTestApplication in loc de PillProntoApp — necesar pt. @HiltAndroidTest.
        testInstrumentationRunner = "com.pillpronto.HiltTestRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${requiredLocalProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${requiredLocalProperty("SUPABASE_PUBLISHABLE_KEY")}\"")
        // Client ID Web din Google Cloud Console — folosit ca serverClientId la Credential Manager
        // (Google verifica app-ul separat, pe baza applicationId+SHA-1, printr-un Client ID Android
        // distinct care nu intra in cod). Acelasi Client ID Web e inregistrat si in Supabase
        // Dashboard -> Auth -> Providers -> Google.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${requiredLocalProperty("GOOGLE_WEB_CLIENT_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Fara Robolectric, android.util.Log (si alte stub-uri android.jar) arunca
            // "not mocked" in teste JVM pure — returneaza valori implicite in loc, silentios.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core) // doar encoder QR (Faza 1.5d) - fara zxing-android-embedded/camera
    // Scaner QR gata facut (UI + camera + permisiune, gestionate de modulul Play Services insusi)
    // - NU e inceputul CameraX/ML Kit din Faza 2 (acela ramane pt. detectie multi-obiect pe cutii
    // de medicamente); aici doar citim textul unui singur cod QR pt. fluxul de invitatie 1.5d.
    implementation(libs.play.services.code.scanner)
    // OCR fallback (Faza 2b-ii) — model Latin bundled (nu play-services-mlkit-text-recognition,
    // care descarca modelul separat prin Play Services, intarziere la prima utilizare); doar text
    // recognition, fara CameraX (o poza + procesare, nu feed continuu).
    implementation(libs.mlkit.text.recognition)
    // Google Sign-In nativ (Faza 1.5b, completare) - Credential Manager, nu WebView/Custom Tabs OAuth.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
