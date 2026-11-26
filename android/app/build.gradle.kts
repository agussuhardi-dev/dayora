import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val dayoraApiUrl = providers.gradleProperty("DAYORA_API_URL").orElse(
    localProperties.getProperty("DAYORA_API_URL", "https://dayora.mocking.my.id")
).get()

val firebaseResources = mapOf(
    "google_app_id" to "FIREBASE_APP_ID",
    "gcm_defaultSenderId" to "FIREBASE_MESSAGING_SENDER_ID",
    "google_api_key" to "FIREBASE_API_KEY",
    "project_id" to "FIREBASE_PROJECT_ID"
).mapValues { (_, property) ->
    providers.gradleProperty(property).orNull?.trim()
        ?: localProperties.getProperty(property)?.trim()
        ?: error("Set $property in android/local.properties or Gradle properties")
}.onEach { (_, value) -> require(value.isNotBlank()) { "Firebase configuration must not be blank" } }

android { namespace = "app.dayora.android"; compileSdk = 36
    buildFeatures { buildConfig = true; resValues = true }
    defaultConfig {
        applicationId = "app.dayora.android"; minSdk = 29; targetSdk = 36; versionCode = 1; versionName = "0.1"
        firebaseResources.forEach { (name, value) -> resValue("string", name, value) }
        buildConfigField("String", "DAYORA_API_URL", "\"${dayoraApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
}

kotlin { jvmToolchain(25) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    ksp("androidx.room:room-compiler:2.8.2")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
}

