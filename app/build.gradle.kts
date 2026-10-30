plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.aryan.vpn"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aryan.vpn"
        minSdk = 26; targetSdk = 34
        versionCode = 1; versionName = "1.0.0"
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    androidResources { generateLocaleConfig = true }
}
dependencies {
    // sing-box core: build libbox.aar from SagerNet/sing-box (make lib_android) and drop it in app/libs
    implementation(files("libs/libbox.aar"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

// Fail early with a useful message instead of a wall of unresolved libbox imports.
val verifyLibboxAar by tasks.registering {
    doLast {
        val aar = file("libs/libbox.aar")
        if (!aar.isFile) {
            throw GradleException("Missing app/libs/libbox.aar. Build sing-box v1.9.7 using scripts/build-libbox.sh before compiling.")
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyLibboxAar) }
