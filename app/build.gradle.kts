import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.smartglasses.safety"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartglasses.safety"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "USE_MOCK_DETECTOR", "false")
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
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"
    }
    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("downloaded-assets"))
        }
    }
}

val efficientDetUrl =
    "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite"
val efficientDetSha256 = "2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b"

fun sha256Hex(file: java.io.File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val downloadEfficientDetLite0 by tasks.registering {
    val dest = layout.buildDirectory.file("downloaded-assets/efficientdet-lite0.tflite")
    outputs.file(dest)
    doLast {
        val outFile = dest.get().asFile
        outFile.parentFile.mkdirs()
        if (outFile.exists() && sha256Hex(outFile) == efficientDetSha256) {
            logger.lifecycle("EfficientDet-Lite0 already present (SHA-256 verified).")
            return@doLast
        }
        try {
            val conn = URI(efficientDetUrl).toURL().openConnection()
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.getInputStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = sha256Hex(outFile)
            if (actual != efficientDetSha256) {
                outFile.delete()
                logger.warn(
                    "EfficientDet-Lite0 SHA-256 mismatch (got $actual). " +
                        "TFLite path skipped; ML Kit remains the default detector."
                )
            } else {
                logger.lifecycle("Downloaded EfficientDet-Lite0 (${outFile.length()} bytes).")
            }
        } catch (e: Exception) {
            logger.warn(
                "Could not download EfficientDet-Lite0 (${e.message}). " +
                    "ML Kit remains the default detector. See models/SOURCE.md."
            )
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(downloadEfficientDetLite0) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.mlkit.object.detection)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
