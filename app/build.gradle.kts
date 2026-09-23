plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets.gradle.plugin)
    id("io.kriptal.ethers.abigen-plugin") version "2.0.1"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.fasterxml.jackson.core") {
            useVersion(libs.versions.jackson.get())
        }
        if (requested.group == "io.netty") {
            useVersion(libs.versions.netty.get())
        }
        if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") {
            useVersion(libs.versions.commonsLang3.get())
        }
    }
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
            isDefault = true
        }

        create("mainnet") {
            dimension = "network"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/DISCLAIMER"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/FastDoubleParser-LICENSE"
            excludes += "META-INF/FastDoubleParser-NOTICE"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/thirdparty-LICENSE"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.ethers.core)
    implementation(libs.ethers.abi)
    implementation(libs.ethers.bom)
    implementation(libs.ethers.signers)
    implementation(libs.ethers.providers)
    implementation(libs.ethers.abigen)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.web3j.crypto)
    implementation(libs.web3j.abi)
    implementation(libs.web3j.utils)
    implementation(libs.web3j.tuples)
    implementation(libs.web3j.core)
    implementation(libs.ens.normalize)
    implementation(libs.okhttp)
    implementation(libs.rxjava2)
    implementation(libs.jetpack.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation(libs.bouncycastle)
    implementation(libs.ens.normalize)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
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
ethersAbigen {
    // set by default
    directorySource("../contracts/abi")

    // set by default
    outputDir = "contracts"

}
val forgeBuild = tasks.register<Exec>("forgeBuild") {
    workingDir = file("../contracts")
    commandLine("forge", "build")

    inputs.dir("../contracts/src")
    outputs.dir("../contracts/out")
}

tasks.named("ethersAbigen") {
    dependsOn(forgeBuild)
}