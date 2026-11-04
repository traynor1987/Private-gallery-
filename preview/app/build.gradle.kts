plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "uk.co.traynor.novaos.preview"
 compileSdk { version = release(37) { minorApiLevel = 1 } }
 defaultConfig {
  applicationId = "uk.co.traynor.novaos.preview"
  minSdk = 37; targetSdk = 37
  versionCode = 2; versionName = "0.2-preview"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 signingConfigs.getByName("debug") {
  storeFile = rootProject.file("dev-signing/preview-debug.keystore")
  storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.browser:browser:1.8.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
}
