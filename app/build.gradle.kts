plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
}

android {
  namespace = "uk.co.traynor.privategallery"
  compileSdk = 36
  defaultConfig {
    applicationId = "uk.co.traynor.privategallery"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
  implementation(libs.androidx.core)
  implementation(libs.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.lifecycle.runtime)
  implementation(libs.lifecycle.viewmodel)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  implementation("org.bouncycastle:bcprov-jdk18on:1.79")
  ksp(libs.room.compiler)
  debugImplementation(libs.compose.tooling)
  testImplementation(libs.junit)
}
