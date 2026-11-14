plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.heroslender.lgtvcontroller"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.heroslender.lgtvcontroller"
        minSdk = 30
        targetSdk = 34

        // CI builds count up with the run number so every build (stable or beta) can be installed
        // over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 1000 + it } ?: 14
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI signs every build with one permanent key (kept in GitHub secrets, never in the repo), so
    // each build installs as an update over the previous one. Without the key (local builds) the
    // temporary debug key is used.
    val ciKeystore = System.getenv("LGREMOTE_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = ciKeystore
                storePassword = System.getenv("LGREMOTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LGREMOTE_KEY_ALIAS")
                keyPassword = System.getenv("LGREMOTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (ciKeystore != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }

        // Same app as the stable one (same package and key), so installing it updates the app.
        create("beta") {
            initWith(getByName("debug"))
            versionNameSuffix = "-beta"
            if (ciKeystore != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.error.prone.annotations)
    implementation(libs.connect.sdk.android.lite)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.gson)

    implementation(libs.glide)
    implementation(libs.glide.compose)
    implementation(libs.glide.okhttp3.integration)
    ksp(libs.ksp.glide.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Dependency Injection
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.ksp.hilt.compiler)

    // Room
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.ksp.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.23.2"
    }

    // Generates the java Protobuf-lite code for the Protobufs in this project. See
    // https://github.com/google/protobuf-gradle-plugin#customizing-protobuf-compilation
    // for more information.
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}


androidComponents {
    onVariants(selector().all()) { variant ->
        afterEvaluate {
            val variantName = variant.name.capitalize()

            val kspTask = project.tasks.findByName("ksp${variantName}Kotlin")
                    as? org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>
            val protoTask = project.tasks.findByName("generate${variantName}Proto")
                    as? com.google.protobuf.gradle.GenerateProtoTask

            kspTask?.run {
                protoTask?.let { setSource(it.outputSourceDirectorySet) }
            }
        }
    }
}