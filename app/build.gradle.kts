plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "uk.co.traynor.privategallery"
  compileSdk { version = release(37) { minorApiLevel = 1 } }
  defaultConfig {
    applicationId = "uk.co.traynor.privategallery"
    minSdk = 37
    targetSdk = 37
    versionCode = 59
    versionName = "1.7.11.3"
    resValue("string", "app_name", "NovaOS")
    buildConfigField("boolean", "HIDDEN_PROTOTYPE", "false")
    buildConfigField("boolean", "HIDDEN_ENABLED", "true")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures { compose = true; buildConfig = true; resValues = true }
  val acceptanceDiagnostics = providers.gradleProperty("PRIVATE_GALLERY_ACCEPTANCE_DIAGNOSTICS").orNull == "true"
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
      buildConfigField("boolean", "ACCEPTANCE_BROWSER_DIAGNOSTICS", acceptanceDiagnostics.toString())
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    getByName("debug") {
      applicationIdSuffix = ".hiddenprototype"
      versionNameSuffix = "-TEST-ONLY"
      resValue("string", "app_name", "NovaOS TEST ONLY")
      buildConfigField("boolean", "HIDDEN_PROTOTYPE", "true")
      buildConfigField("boolean", "ACCEPTANCE_BROWSER_DIAGNOSTICS", "false")
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
}

dependencies {
  implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
  implementation("androidx.camera:camera-camera2:1.5.3")
  implementation("androidx.camera:camera-lifecycle:1.5.3")
  implementation("androidx.camera:camera-view:1.5.3")
  implementation("androidx.camera:camera-video:1.5.3")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("info.guardianproject:tor-android:0.4.9.14")
  implementation(libs.androidx.core)
  implementation(libs.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  implementation(libs.lifecycle.runtime)
  implementation(libs.lifecycle.viewmodel)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment)
  implementation("androidx.webkit:webkit:1.13.0")
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.ui)
  implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
  implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
  implementation("androidx.media3:media3-transformer:1.5.1")
  implementation(libs.paging.runtime)
  implementation(libs.paging.compose)
  implementation(libs.wireguard.tunnel)
  implementation("org.bouncycastle:bcprov-jdk18on:1.79")
  implementation("org.json:json:20240303")
  debugImplementation(libs.compose.tooling)
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  testImplementation(libs.junit)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// Pinned public OCR model, prepared only at build time and always bundled for offline use.
abstract class PrepareEvidence179ModelTask : Exec() {
  @get:org.gradle.api.tasks.OutputDirectory
  abstract val assetsDirectory: org.gradle.api.file.DirectoryProperty
}
val prepareEvidence179Model by tasks.registering(PrepareEvidence179ModelTask::class) {
  assetsDirectory.set(layout.buildDirectory.dir("generated/evidence179Assets"))
  val model = assetsDirectory.file("tessdata/eng.traineddata")
  inputs.file(rootProject.file("scripts/prepare_evidence179_model.sh"))
  inputs.file(project.file("src/main/assets/evidence179/model-manifest.json"))
  // Validate even a previously prepared file; corrupt caches must fail closed.
  outputs.upToDateWhen { false }
  commandLine("bash", rootProject.file("scripts/prepare_evidence179_model.sh"), model.get().asFile)
}
androidComponents.onVariants { variant ->
  variant.sources.assets?.addGeneratedSourceDirectory(
    prepareEvidence179Model, PrepareEvidence179ModelTask::assetsDirectory
  )
}
tasks.named("preBuild").configure { dependsOn(prepareEvidence179Model) }
