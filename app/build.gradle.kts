plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

val releaseKeystorePath = System.getenv("POS_RELEASE_KEYSTORE_PATH").orEmpty()
val releaseKeystorePassword = System.getenv("POS_RELEASE_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("POS_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("POS_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

fun releaseSigningFailureMessage(): String =
    "Release signing requires POS_RELEASE_KEYSTORE_PATH, POS_RELEASE_KEYSTORE_PASSWORD, " +
        "POS_RELEASE_KEY_ALIAS, and POS_RELEASE_KEY_PASSWORD. Release builds must not use " +
        "debug signing or produce unsigned artifacts."

android {
    namespace = "com.enterprise.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enterprise.pos"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "STRIPE_LOCATION_ID", "\"${project.findProperty("stripeLocationId") ?: ""}\"")
        buildConfigField("String", "SQUARE_APPLICATION_ID", "\"${project.findProperty("squareApplicationId") ?: ""}\"")
        buildConfigField("String", "SHOPIFY_SHOP_DOMAIN", "\"${project.findProperty("shopifyShopDomain") ?: ""}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${project.findProperty("backendBaseUrl") ?: "https://pos.example.com"}\"")
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                val keystoreFile = file(releaseKeystorePath)
                storeFile = keystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "ENABLE_DEMO_DATA", "true")
            buildConfigField("boolean", "ENABLE_SIMULATED_PROVIDERS", "true")
            buildConfigField("boolean", "ENABLE_MANUAL_CARD_ENTRY", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "ENABLE_DEMO_DATA", "false")
            buildConfigField("boolean", "ENABLE_SIMULATED_PROVIDERS", "false")
            buildConfigField("boolean", "ENABLE_MANUAL_CARD_ENTRY", "false")
        }
    }
    sourceSets {
        getByName("main") {
            java.exclude(
                "com/enterprise/pos/ui/components/PosBadge.kt",
                "com/enterprise/pos/ui/components/PosButton.kt",
                "com/enterprise/pos/ui/components/PosCard.kt",
                "com/enterprise/pos/ui/components/PosDialog.kt",
                "com/enterprise/pos/ui/components/PosDivider.kt",
                "com/enterprise/pos/ui/components/PosDropdownField.kt",
                "com/enterprise/pos/ui/components/PosEmptyState.kt",
                "com/enterprise/pos/ui/components/PosForm.kt",
                "com/enterprise/pos/ui/components/PosResponsive.kt",
                "com/enterprise/pos/ui/components/PosSearch.kt",
                "com/enterprise/pos/ui/components/PosSwitchField.kt",
                "com/enterprise/pos/ui/components/PosTable.kt",
                "com/enterprise/pos/ui/components/PosTextField.kt",
                "com/enterprise/pos/ui/theme/**"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        managedDevices {
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("tablet") {
                    device = "Pixel Tablet"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

tasks.register("validateReleaseSigning") {
    doLast {
        if (!releaseSigningConfigured) {
            throw org.gradle.api.GradleException(releaseSigningFailureMessage())
        }
        if (!file(releaseKeystorePath).exists()) {
            throw org.gradle.api.GradleException("Release keystore not found at POS_RELEASE_KEYSTORE_PATH=$releaseKeystorePath")
        }
    }
}

tasks.matching { task ->
    task.name == "preReleaseBuild" ||
        task.name == "packageRelease" ||
        task.name == "assembleRelease" ||
        task.name == "bundleRelease"
}.configureEach {
    dependsOn("validateReleaseSigning")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ui"))
    implementation(project(":payment-api"))
    implementation(project(":payment-stripe"))
    implementation(project(":payment-square"))
    implementation(project(":payment-shopify"))
    implementation(project(":hardware"))
    implementation(project(":feature-restaurant"))
    implementation(project(":feature-catalog"))
    implementation(project(":feature-sales"))
    implementation(project(":feature-customers"))
    implementation(project(":feature-employees"))
    implementation(project(":feature-reports"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-inventory"))
    implementation(project(":feature-kds"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-migration"))
    implementation(project(":feature-shifts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
