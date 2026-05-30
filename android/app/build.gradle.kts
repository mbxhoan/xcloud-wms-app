plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "vn.delfi.xcloudwms"
    compileSdk = 35

    defaultConfig {
        applicationId = "vn.delfi.xcloudwms"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "APP_CHANNEL", "\"SCANNER_NATIVE\"")
    }

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BUILD_ENV", "\"dev\"")
            buildConfigField("String", "BASE_API_URL", "\"https://dev-api.example.invalid/\"")
            buildConfigField("boolean", "ENABLE_CAMERA_SCAN_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_DEVICE_LICENSE_CHECK", "false")
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "BUILD_ENV", "\"staging\"")
            buildConfigField("String", "BASE_API_URL", "\"https://staging-api.example.invalid/\"")
            buildConfigField("boolean", "ENABLE_CAMERA_SCAN_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_DEVICE_LICENSE_CHECK", "false")
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BUILD_ENV", "\"prod\"")
            buildConfigField("String", "BASE_API_URL", "\"https://api.example.invalid/\"")
            buildConfigField("boolean", "ENABLE_CAMERA_SCAN_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_DEVICE_LICENSE_CHECK", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
