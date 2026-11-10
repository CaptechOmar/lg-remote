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

        versionCode = 14
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // An optional fixed key for the beta app, so one beta can be installed over the previous one.
    // Without the key file, beta builds are signed with the temporary debug key instead.
    val betaKeystore = file("beta-test.keystore")

    signingConfigs {
        if (betaKeystore.exists()) {
            create("beta") {
                storeFile = betaKeystore
                storePassword = "lgremotebeta"
                keyAlias = "beta"
                keyPassword = "lgremotebeta"
            }
        }
    }

    buildTypes {
        // Installs next to the stable app (own package name, name and data) to try new things
        create("beta") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            if (betaKeystore.exists()) {
                signingConfig = signingConfigs.getByName("beta")
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
    // Every beta build needs a higher version code than the last to install over it. The CI run
    // number only ever goes up.
    onVariants(selector().withBuildType("beta")) { variant ->
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        variant.outputs.forEach { output -> output.versionCode.set(2000 + runNumber) }
    }

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