pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Stripe Terminal SDK maven repo
        maven { url = uri("https://maven.google.com") }
    }
}

rootProject.name = "EnterprisePOS"

include(":backend")
include(":app")
include(":core")
include(":domain")
include(":data")
include(":payment-api")
include(":payment-stripe")
include(":payment-square")
include(":payment-shopify")
include(":hardware")
include(":feature-restaurant")
include(":feature-catalog")
include(":feature-sales")
include(":feature-customers")
include(":feature-employees")
include(":feature-reports")
include(":feature-dashboard")
include(":feature-inventory")
include(":feature-kds")
include(":feature-settings")
include(":feature-migration")
include(":feature-shifts")
