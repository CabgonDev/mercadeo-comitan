plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.googleService)
    alias(libs.plugins.firebase.crashlytics)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
android {
    namespace = "com.cabgon.mercadeocomitan"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.cabgon.mercadeocomitan"
        minSdk = 24
        targetSdk = 37
        versionCode = 306
        versionName = "3.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

configurations.configureEach {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.security.crypto)
    implementation(libs.shimmer)
    implementation(libs.cicleImage)
    implementation(libs.photoview)
    implementation(libs.ccp)
    implementation(libs.slf4j.nop)
    implementation(libs.lottie)

    implementation(libs.billing.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.auth.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebaseAuth)
    implementation(libs.firebaseDatabase)
    implementation(libs.firebaseStorage)
    implementation(libs.messaging)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    //noinspection LoginCredentials
    implementation(libs.androidx.credentials)
    //noinspection LoginCredentials
    implementation(libs.androidx.credentials.play.services.auth)
    //noinspection LoginCredentials
    implementation(libs.googleid)
    //noinspection LoginCredentials
    implementation(libs.play.services.auth)

    implementation(libs.play.services.ads)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.app.update.ktx)
    implementation(libs.integrity)
    implementation(libs.glide)

    implementation(libs.google.api.client) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation(libs.google.auth.library.oauth2.http) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    implementation(platform(libs.kotlin.bom))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
