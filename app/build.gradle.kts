plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets.gradle.plugin)
}

android {
    namespace = "com.voltic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voltic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"



    }
    flavorDimensions += "network"

    productFlavors {
        create("sepolia") {
            dimension = "network"
            applicationIdSuffix = ".sepolia"
            versionNameSuffix = "-sepolia"

            buildConfigField("String", "ARBITRUM_RPC_URL", "\"https://sepolia-rollup.arbitrum.io/rpc\"")
            buildConfigField("Long", "ARBITRUM_CHAIN_ID", "421614L")
            buildConfigField("String", "ARBITRUM_CHAIN_NAME", "\"Arbitrum Sepolia\"")
            buildConfigField("String", "EXPLORER_URL", "\"https://sepolia.arbiscan.io\"")
            buildConfigField("String", "ENS_RPC_URL", "\"https://eth.llamarpc.com\"")
            buildConfigField("String", "VAULT_ADDRESS", "\"0x2EB9cD3C24C7cA7F7Eb7e563Be14C7Dd60504B6e\"")
        }

        create("mainnet") {
            dimension = "network"

            buildConfigField("String", "ARBITRUM_RPC_URL", "\"https://arb1.arbitrum.io/rpc\"")
            buildConfigField("Long", "ARBITRUM_CHAIN_ID", "42161L")
            buildConfigField("String", "ARBITRUM_CHAIN_NAME", "\"Arbitrum One\"")
            buildConfigField("String", "EXPLORER_URL", "\"https://arbiscan.io\"")
            buildConfigField("String", "ENS_RPC_URL", "\"https://eth.llamarpc.com\"")
            buildConfigField("String", "VAULT_ADDRESS", "\"0xb84b1abe962534917e9f5f7945315f309cd36fa4\"")
        }
    }
    buildTypes {
        release {

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/FastDoubleParser-LICENSE"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/FastDoubleParser-NOTICE"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/thirdparty-LICENSE"


        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.web3j.crypto)
    implementation(libs.web3j.core)
    implementation(libs.jetpack.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)


}
secrets {
    defaultPropertiesFileName = "local.properties"
}