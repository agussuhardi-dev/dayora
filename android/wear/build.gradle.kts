plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="app.dayora.wear"; compileSdk=36
 defaultConfig { applicationId="app.dayora.android"; minSdk=30; targetSdk=36; versionCode=1; versionName="0.1" }
}
kotlin { jvmToolchain(25) }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.01"))
 implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.wear:wear:1.3.0")
 implementation("androidx.wear.compose:compose-material:1.4.1")
 implementation("androidx.wear.compose:compose-foundation:1.5.6")
 implementation("com.google.android.gms:play-services-wearable:19.0.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}
