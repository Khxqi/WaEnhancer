plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.khxqi.airpodsxaplfix"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.khxqi.airpodsxaplfix"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}
