plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.enterprise.pos.ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("../app/src/main/java")
            java.include("com/enterprise/pos/ui/components/**")
            java.include("com/enterprise/pos/ui/theme/**")
            java.exclude("com/enterprise/pos/ui/components/PosComponents.kt")
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
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.material.icons.extended)
    api(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
