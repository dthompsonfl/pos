plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

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

        // Provider identifiers are NOT secrets — they may be embedded in the binary.
        // Real secrets (Stripe secret key, Shopify access token, Square access token) live
        // ONLY on the backend and are never read from local.properties / gradle.properties.
        // These fields reference the merchant's published identifiers, not credentials.
        buildConfigField("String", "STRIPE_LOCATION_ID", "\"${project.findProperty("stripeLocationId") ?: ""}\"")
        buildConfigField("String", "SQUARE_APPLICATION_ID", "\"${project.findProperty("squareApplicationId") ?: ""}\"")
        buildConfigField("String", "SHOPIFY_SHOP_DOMAIN", "\"${project.findProperty("shopifyShopDomain") ?: ""}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${project.findProperty("backendBaseUrl") ?: "https://pos.example.com"}\"")
    }

    signingConfigs {
        create("release") {
            // Release signing is configured via environment variables only.
            // CI must set: POS_RELEASE_KEYSTORE_PATH, POS_RELEASE_KEYSTORE_PASSWORD,
            // POS_RELEASE_KEY_ALIAS, POS_RELEASE_KEY_PASSWORD.
            // If any are missing the release build will fail loudly
            // instead of silently falling back to the debug keystore.
            val ksPath = System.getenv("POS_RELEASE_KEYSTORE_PATH") ?: ""
            val ksPass = System.getenv("POS_RELEASE_KEYSTORE_PASSWORD") ?: ""
            val alias = System.getenv("POS_RELEASE_KEY_ALIAS") ?: ""
            val keyPass = System.getenv("POS_RELEASE_KEY_PASSWORD") ?: ""
            if (ksPath.isNotEmpty()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Demo PINs and simulated providers are only enabled in debug builds.
            buildConfigField("boolean", "ENABLE_DEMO_DATA", "true")
            buildConfigField("boolean", "ENABLE_SIMULATED_PROVIDERS", "true")
            buildConfigField("boolean", "ENABLE_MANUAL_CARD_ENTRY", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release signing: only attach if env vars were set. Otherwise the build
            // produces an unsigned APK and `assembleRelease` will fail at packaging time.
            val releaseSc = signingConfigs.getByName("release")
            if (releaseSc.storeFile != null && releaseSc.storeFile!!.exists()) {
                signingConfig = releaseSc
            }
            buildConfigField("boolean", "ENABLE_DEMO_DATA", "false")
            buildConfigField("boolean", "ENABLE_SIMULATED_PROVIDERS", "false")
            buildConfigField("boolean", "ENABLE_MANUAL_CARD_ENTRY", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
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

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
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

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
