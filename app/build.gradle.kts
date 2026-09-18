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
    versionCode = 10
    versionName = "1.0.9"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures { compose = true; buildConfig = true }
  val releaseStoreFile = providers.gradleProperty("PRIVATE_GALLERY_STORE_FILE").orNull
  val releaseStorePassword = providers.gradleProperty("PRIVATE_GALLERY_STORE_PASSWORD").orNull
  val releaseKeyAlias = providers.gradleProperty("PRIVATE_GALLERY_KEY_ALIAS").orNull
  val releaseKeyPassword = providers.gradleProperty("PRIVATE_GALLERY_KEY_PASSWORD").orNull
  val releaseSigningConfigured = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }
  if (releaseSigningConfigured) {
    signingConfigs.create("release") {
      storeFile = file(releaseStoreFile!!)
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      keyPassword = releaseKeyPassword
    }
  }
  buildTypes {
    getByName("release") {
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }
  tasks.configureEach {
    if (name.contains("release", ignoreCase = true)) {
      doFirst {
        check(releaseSigningConfigured) {
          "Release builds require the permanent Private Gallery signing credentials. Refusing a debug-signed release."
        }
      }
    }
  }
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
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation(libs.paging.runtime)
  implementation(libs.paging.compose)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  implementation("org.bouncycastle:bcprov-jdk18on:1.79")
  implementation("org.json:json:20240303")
  ksp(libs.room.compiler)
  debugImplementation(libs.compose.tooling)
  testImplementation(libs.junit)
}
