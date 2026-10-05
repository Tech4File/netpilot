plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.netpilot.openvpn.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    // The native library is NOT built here (deliberately): scripts/
    // build-ovpn-native.sh + the CI native job produce libovpncore.so into
    // src/main/jniLibs/<abi>/, which gets packaged automatically when
    // present. Builds without it are identical minus the engine.
    sourceSets.getByName("main") {
        jniLibs.srcDir("src/main/jniLibs")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
}
