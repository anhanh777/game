plugins {
    id("com.android.library")
}

android {
    compileSdk = 34
    namespace = "ru.playsoftware.j2meloader.dexlib"

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    implementation("org.ow2.asm:asm:9.6")

    testImplementation("junit:junit:4.13.2")
}
