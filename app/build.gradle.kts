import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val signingKeystorePath = providers.environmentVariable("IQMEMO_SIGNING_STORE").orNull
    ?: localProperties.getProperty("IQMEMO_SIGNING_STORE")


// Read only local/environment configuration; never print credential values.
fun speechProperty(name: String, placeholder: String): String =
    providers.environmentVariable(name).orNull ?: localProperties.getProperty(name, placeholder)

fun javaString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

android {
    namespace = "com.example.iqmemo"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "io.github.ningbowhu.iqmemo"
        minSdk = 30
        targetSdk = 35
        versionCode = 22
        versionName = "5.5.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "XFYUN_APP_ID", javaString(speechProperty("XFYUN_APP_ID", "your_app_id")))
        buildConfigField("String", "XFYUN_API_KEY", javaString(speechProperty("XFYUN_API_KEY", "your_api_key")))
        buildConfigField("String", "XFYUN_API_SECRET", javaString(speechProperty("XFYUN_API_SECRET", "your_api_secret")))
    }

    signingConfigs {
        create("stableLocal") {
            if (!signingKeystorePath.isNullOrBlank()) {
                storeFile = file(signingKeystorePath)
                storePassword = providers.environmentVariable("IQMEMO_SIGNING_STORE_PASSWORD").orNull
                    ?: localProperties.getProperty("IQMEMO_SIGNING_STORE_PASSWORD")
                keyAlias = providers.environmentVariable("IQMEMO_SIGNING_KEY_ALIAS").orNull
                    ?: localProperties.getProperty("IQMEMO_SIGNING_KEY_ALIAS")
                keyPassword = providers.environmentVariable("IQMEMO_SIGNING_KEY_PASSWORD").orNull
                    ?: localProperties.getProperty("IQMEMO_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            // Release signing uses the maintainer's own local credentials; never commit a key.
            signingConfig = signingConfigs.getByName("stableLocal")
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
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// Mandatory before formal signing/output. Debug and unit/lint checks remain available.
// Read-only approval checker; private historical approvals are not distributed.
val verifyUiApproval by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    val python = providers.environmentVariable("IQMEMO_PYTHON").orNull
        ?: if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(python, rootProject.file(".agents/scripts/verify_release.py").absolutePath,
        "--project", rootProject.projectDir.absolutePath,
        "--version", android.defaultConfig.versionName!!)
}
tasks.configureEach {
    if (name in setOf("validateSigningRelease", "packageRelease", "signReleaseBundle", "bundleRelease", "assembleRelease")) {
        dependsOn(verifyUiApproval)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
