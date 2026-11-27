plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wasif.translucentnotify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wasif.translucentnotify"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "7.1.2"
    }

    signingConfigs {
        // Filled from environment variables set by the GitHub workflow.
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
}
