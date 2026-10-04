plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "uk.co.traynor.privategallery"
  compileSdk = 36
  ndkVersion = "27.3.13750724"
  externalNativeBuild {
    cmake { path = file("src/main/c/CMakeLists.txt"); version = "3.22.1" }
  }
  defaultConfig {
    applicationId = "uk.co.traynor.privategallery"
    minSdk = 26
    targetSdk = 36
    versionCode = 28
    versionName = "1.0.27"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
  }
  buildFeatures { compose = true; buildConfig = true }
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
    // Synthetic-only package. Normal debug/release instrumentation continues to target debug.
    create("phase0Evidence") {
      initWith(getByName("debug"))
      applicationIdSuffix = ".phase0evidence"
      versionNameSuffix = "-phase0-evidence"
      matchingFallbacks += listOf("debug")
      buildConfigField("boolean", "ACCEPTANCE_BROWSER_DIAGNOSTICS", "false")
    }
    getByName("release") {
      buildConfigField("boolean", "ACCEPTANCE_BROWSER_DIAGNOSTICS", acceptanceDiagnostics.toString())
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    getByName("debug") {
      buildConfigField("boolean", "ACCEPTANCE_BROWSER_DIAGNOSTICS", "false")
    }
  }
  if (providers.gradleProperty("PRIVATE_GALLERY_PHASE0_EVIDENCE").orNull == "true") {
    testBuildType = "phase0Evidence"
    // Keep ordinary production-browser tests out of the isolated evidence package.
    sourceSets.getByName("androidTest").java.setSrcDirs(emptyList<String>())
    sourceSets.getByName("androidTest").res.setSrcDirs(emptyList<String>())
    sourceSets.getByName("androidTest").assets.setSrcDirs(emptyList<String>())
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

// Actual host-JVM JNI/kernel fixtures are distinct from packaged Android/ART proof.
// Compile the identical C source with host JNI headers, without mock/injection code.
val hostStagingEntropy = tasks.register<Exec>("compileHostStagingEntropy") {
  val source = file("src/main/c/staging_entropy.c")
  val output = layout.buildDirectory.file("host-staging-entropy/libpg_staging_entropy.so")
  inputs.file(source)
  outputs.file(output)
  doFirst {
    output.get().asFile.parentFile.mkdirs()
    val headers = File(System.getProperty("java.home"), "include")
    commandLine("gcc", "-std=c11", "-O2", "-Wall", "-Wextra", "-Werror", "-fPIC", "-shared",
      "-fvisibility=hidden", "-fstack-protector-strong", "-I${headers.absolutePath}",
      "-I${File(headers, "linux").absolutePath}", source.absolutePath, "-o", output.get().asFile.absolutePath)
  }
}
tasks.withType<Test>().configureEach {
  dependsOn(hostStagingEntropy)
  doFirst { jvmArgs("-Djava.library.path=${layout.buildDirectory.dir("host-staging-entropy").get().asFile.absolutePath}") }
}

dependencies {
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
  implementation("androidx.webkit:webkit:1.12.1")
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
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
