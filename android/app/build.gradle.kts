import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun resolveBuildValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        providers.gradleProperty(key).orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: localProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

val devConnectionUrl = resolveBuildValue("SUPABASE_URL_DEV", "BASE_API_URL_DEV")
val devAnonKey = resolveBuildValue("SUPABASE_ANON_KEY_DEV", "ANON_KEY_DEV")
val devDefaultOperatorCode = resolveBuildValue("DEFAULT_OPERATOR_CODE_DEV")
val devDefaultPassword = resolveBuildValue("DEFAULT_PASSWORD_DEV")
val devAutoLogin = resolveBuildValue("AUTO_LOGIN_DEV").equals("true", ignoreCase = true)
val stagingConnectionUrl = resolveBuildValue("SUPABASE_URL_STAGING", "BASE_API_URL_STAGING")
val stagingAnonKey = resolveBuildValue("SUPABASE_ANON_KEY_STAGING", "ANON_KEY_STAGING")
val stagingDefaultOperatorCode = resolveBuildValue("DEFAULT_OPERATOR_CODE_STAGING")
val stagingDefaultPassword = resolveBuildValue("DEFAULT_PASSWORD_STAGING")
val stagingAutoLogin = resolveBuildValue("AUTO_LOGIN_STAGING").equals("true", ignoreCase = true)
val prodConnectionUrl = resolveBuildValue("SUPABASE_URL_PROD", "BASE_API_URL_PROD")
val prodAnonKey = resolveBuildValue("SUPABASE_ANON_KEY_PROD", "ANON_KEY_PROD")
val prodDefaultOperatorCode = resolveBuildValue("DEFAULT_OPERATOR_CODE_PROD")
val prodDefaultPassword = resolveBuildValue("DEFAULT_PASSWORD_PROD")
val prodAutoLogin = resolveBuildValue("AUTO_LOGIN_PROD").equals("true", ignoreCase = true)

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
            buildConfigField(
                "String",
                "BASE_API_URL",
                devConnectionUrl.ifBlank { "https://dev-api.example.invalid/" }.asBuildConfigString(),
            )
            buildConfigField("String", "DEFAULT_CONNECTION_URL", devConnectionUrl.asBuildConfigString())
            buildConfigField("String", "DEFAULT_CONNECTION_ANON_KEY", devAnonKey.asBuildConfigString())
            buildConfigField("String", "DEFAULT_OPERATOR_CODE", devDefaultOperatorCode.asBuildConfigString())
            buildConfigField("String", "DEFAULT_PASSWORD", devDefaultPassword.asBuildConfigString())
            buildConfigField("boolean", "AUTO_LOGIN_ON_LAUNCH", devAutoLogin.toString())
            buildConfigField("boolean", "ENABLE_CAMERA_SCAN_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_DEVICE_LICENSE_CHECK", "true")
        }

        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "BUILD_ENV", "\"staging\"")
            buildConfigField(
                "String",
                "BASE_API_URL",
                stagingConnectionUrl.ifBlank { "https://staging-api.example.invalid/" }.asBuildConfigString(),
            )
            buildConfigField("String", "DEFAULT_CONNECTION_URL", stagingConnectionUrl.asBuildConfigString())
            buildConfigField("String", "DEFAULT_CONNECTION_ANON_KEY", stagingAnonKey.asBuildConfigString())
            buildConfigField("String", "DEFAULT_OPERATOR_CODE", stagingDefaultOperatorCode.asBuildConfigString())
            buildConfigField("String", "DEFAULT_PASSWORD", stagingDefaultPassword.asBuildConfigString())
            buildConfigField("boolean", "AUTO_LOGIN_ON_LAUNCH", stagingAutoLogin.toString())
            buildConfigField("boolean", "ENABLE_CAMERA_SCAN_FALLBACK", "true")
            buildConfigField("boolean", "ENABLE_DEVICE_LICENSE_CHECK", "true")
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BUILD_ENV", "\"prod\"")
            buildConfigField(
                "String",
                "BASE_API_URL",
                prodConnectionUrl.ifBlank { "https://api.example.invalid/" }.asBuildConfigString(),
            )
            buildConfigField("String", "DEFAULT_CONNECTION_URL", prodConnectionUrl.asBuildConfigString())
            buildConfigField("String", "DEFAULT_CONNECTION_ANON_KEY", prodAnonKey.asBuildConfigString())
            buildConfigField("String", "DEFAULT_OPERATOR_CODE", prodDefaultOperatorCode.asBuildConfigString())
            buildConfigField("String", "DEFAULT_PASSWORD", prodDefaultPassword.asBuildConfigString())
            buildConfigField("boolean", "AUTO_LOGIN_ON_LAUNCH", prodAutoLogin.toString())
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
