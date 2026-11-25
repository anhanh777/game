plugins {
    id("com.android.library")
}

android {
    namespace = "ru.playsoftware.j2meloader"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        buildConfigField("boolean", "FULL_EMULATOR", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path("src/main/cpp/Android.mk")
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        prefab = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":dexlib"))

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.preference:preference:1.2.1")

    implementation("com.android.volley:volley:1.2.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:33.0.0-android")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("net.lingala.zip4j:zip4j:2.11.6")

    implementation("com.github.nikita36078:pngj:2.2.3")
    implementation("io.github.nikita36078:ffmpeg-kit:6.0.LTS")
    implementation("com.google.oboe:oboe:1.8.0")

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.github.nikita36078:filepicker:4.3.0")
    implementation("com.github.yukuku:ambilwarna:2.0.1")

    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}
