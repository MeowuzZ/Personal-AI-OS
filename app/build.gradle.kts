import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun secretFromEnvironment(valueName: String, fileName: String): String? {
    val directValue = System.getenv(valueName)?.trim()
    if (!directValue.isNullOrEmpty()) return directValue

    val secretFile = System.getenv(fileName)?.trim()
    return secretFile
        ?.takeIf { it.isNotEmpty() }
        ?.let { File(it).readText().trim() }
        ?.takeIf { it.isNotEmpty() }
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")?.trim()
val releaseStorePassword = secretFromEnvironment(
    valueName = "ANDROID_KEYSTORE_PASSWORD",
    fileName = "ANDROID_KEYSTORE_PASSWORD_FILE",
)
val releaseKeyPassword = secretFromEnvironment(
    valueName = "ANDROID_KEY_PASSWORD",
    fileName = "ANDROID_KEY_PASSWORD_FILE",
) ?: releaseStorePassword
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.trim().orEmpty().ifEmpty { "personal-ai-os" }
val releaseSigningConfigured = !releaseKeystorePath.isNullOrEmpty() &&
    !releaseStorePassword.isNullOrEmpty() &&
    !releaseKeyPassword.isNullOrEmpty()

android {
    namespace = "com.selavie.zhixing"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.selavie.zhixing"
        minSdk = 26
        targetSdk = 33
        versionCode = 6
        versionName = "0.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.4.0" }
    packagingOptions.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.compose.ui:ui:1.4.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.4.3")
    implementation("androidx.compose.foundation:foundation:1.4.3")
    implementation("androidx.compose.material:material:1.4.3")
    implementation("androidx.compose.material:material-icons-core:1.4.3")
    implementation("androidx.compose.material:material-ripple:1.4.3")
    implementation("androidx.compose.material3:material3:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.4.3")
    testImplementation("junit:junit:4.13.2")
}
