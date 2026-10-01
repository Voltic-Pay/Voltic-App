plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets.gradle.plugin)
    id("io.kriptal.ethers.abigen-plugin") version "2.0.1"
}


android {
    namespace = "com.voltic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voltic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4-SNAPSHOT"

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
    implementation(platform (libs.ethers.bom))
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
class LibDirMissingSpec(private val libDir: File) : Spec<Task> {
    override fun isSatisfiedBy(element: Task): Boolean {
        return !libDir.exists() || (libDir.listFiles()?.isEmpty() ?: true)
    }
}
val contractsDir = file("../contracts")

val forgeDeps = mapOf(
    "openzeppelin-contracts" to "OpenZeppelin/openzeppelin-contracts@v5.7.0",
    "forge-std" to "foundry-rs/forge-std" // TODO: pin a tag once you pick one
)

fun missingForgeDeps(): List<Pair<String, String>> =
    forgeDeps.filter { (name, _) ->
        File(contractsDir, "lib/$name").listFiles().isNullOrEmpty()
    }.toList()

fun forgeAvailable(): Boolean = runCatching {
    ProcessBuilder("forge", "--version")
        .redirectErrorStream(true).start().waitFor() == 0
}.getOrDefault(false)

val forgeInstall = tasks.register<Exec>("forgeInstall") {
    val targetDir = file("../contracts")
    val requiredDeps = mapOf(
        "openzeppelin-contracts" to "OpenZeppelin/openzeppelin-contracts@v5.7.0",
        "forge-std" to "foundry-rs/forge-std"
    )

    workingDir = targetDir

    doFirst {
        val forgeExists = runCatching {
            ProcessBuilder("forge", "--version").redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)

        if (!forgeExists) {
            throw GradleException(
                "Foundry not found. Install it: curl -L https://foundry.paradigm.xyz | bash && foundryup"
            )
        }

        val missing = requiredDeps.filter { (name, _) ->
            File(targetDir, "lib/$name").listFiles().isNullOrEmpty()
        }

        if (missing.isNotEmpty()) {
            missing.forEach { (name, _) -> File(targetDir, "lib/$name").deleteRecursively() }
            commandLine(listOf("forge", "install", "--no-git") + missing.values)
        } else {
            commandLine("echo", "All forge dependencies up to date")
        }
    }
}

val forgeBuild = tasks.register<Exec>("forgeBuild") {
    dependsOn(forgeInstall)
    workingDir = contractsDir
    commandLine("forge", "build")
    inputs.dir("../contracts/src")
    inputs.dir("../contracts/lib")
    outputs.dir("../contracts/out")
}
ethersAbigen {
    directorySource("../contracts/out/VolticSmartWallet.sol") {
        packageOverride.set("com.voltic.contracts")
    }
}
android {
    sourceSets {
        named("main") {
            kotlin.srcDir(file("build/generated/source/ethers/main/kotlin"))
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn("ethersAbigen")
}
tasks.named("ethersAbigen") {
    dependsOn(forgeBuild)
}
