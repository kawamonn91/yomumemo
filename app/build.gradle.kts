import java.util.Properties

/**
 * Google Books API 繧ｭ繝ｼ縺ｯ local.properties 縺九ｉ隱ｭ繧縲・
 * local.properties 縺ｯ .gitignore 貂医∩縺ｪ縺ｮ縺ｧ縲√く繝ｼ縺後Μ繝昴ず繝医Μ縺ｫ蜈･繧峨↑縺・・
 * 譛ｪ險ｭ螳壹〒繧ゅン繝ｫ繝峨・騾壹ｊ縲√◎縺ｮ蝣ｴ蜷医い繝励Μ縺ｯ openBD 縺ｮ縺ｿ縺ｧ蜍穂ｽ懊☆繧・陦ｨ邏吶・繝励Ξ繝ｼ繧ｹ繝帙Ν繝)縲・
 */
val googleBooksApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}.getProperty("googleBooksApiKey").orEmpty()

/**
 * 繝ｪ繝ｪ繝ｼ繧ｹ鄂ｲ蜷阪・諠・ｱ縲Ｌeystore.properties 縺ｯ .gitignore 貂医∩縺ｧ縲・
 * 骰ｵ縺昴・繧ゅ・繧ゅΜ繝昴ず繝医Μ縺ｫ縺ｯ蜈･繧峨↑縺・・
 * 繝輔ぃ繧､繝ｫ縺檎┌縺・腸蠅・〒縺ｯ繝・ヰ繝・げ鄂ｲ蜷阪・縺ｾ縺ｾ繝薙Ν繝峨′騾壹ｋ繧医≧縺ｫ縺励※縺ゅｋ
 * (CI 繧・ｻ悶・髢狗匱閠・′骰ｵ縺ｪ縺励〒繧ゅン繝ｫ繝峨ｒ遒ｺ隱阪〒縺阪ｋ繧医≧縺ｫ縺吶ｋ縺溘ａ)縲・
 */
val releaseSigning: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "jp.yomumemo.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.yomumemo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"" + googleBooksApiKey + "\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // 譛ｬ譚･縺ｯtrue縺ｫ縺励◆縺・′縲ヽ8(shrink/obfuscate縲・dontoptimize縺ｫ縺励※繧ょ・迴ｾ)縺・
            // CameraX縺ｮProcessCameraProvider髱槫酔譛溘さ繝ｼ繝ｫ繝舌ャ繧ｯ + Compose(AndroidView)縺ｮ
            // 邨・∩蜷医ｏ縺帙〒NullPointerException繧堤函繧荳榊・蜷医ｒ螳滓ｩ溷・迴ｾ縺ｧ遒ｺ隱阪＠縺・
            // (debug繝薙Ν繝・R8縺ｪ縺励〒縺ｯ蜀咲樟縺励↑縺・縲ょ次蝗縺ｮR8譛驕ｩ蛹悶ｒ迚ｹ螳壹〒縺阪ｋ縺ｾ縺ｧ縺ｮ
            // 證ｫ螳壼・鄂ｮ縺ｨ縺励※辟｡蜉ｹ蛹悶☆繧九５ODO: 蜴溷屏繧堤音螳壹＠谺｡隨ｬ true 縺ｫ謌ｻ縺吶・
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        localeFilters += listOf("ja", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}


room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 繝舌・繧ｳ繝ｼ繝芽ｪｭ縺ｿ蜿悶ｊ: 繧ｪ繝ｳ繝・ヰ繧､繧ｹ縺ｮML Kit(蠑慕畑譁・・隱ｭ縺ｿ蜿悶ｊ縺ｨ蜷後§CameraX譁ｹ蠑・
    implementation(libs.barcode.scanning)

    // 陦ｨ邏吶・閾ｪ蛻・聴繧顔匳骭ｲ: Google繝峨Λ繧､繝悶・譖ｸ鬘槭せ繧ｭ繝｣繝ｳ縺ｨ蜷後§霎ｺ讀懷・繝ｻ繝医Μ繝溘Φ繧ｰ讖溯・
    implementation(libs.mlkit.document.scanner)

    // Google繝峨Λ繧､繝悶∈縺ｮ閾ｪ蜍輔ヰ繝・け繧｢繝・・(drive.file繧ｹ繧ｳ繝ｼ繝励・隱榊庄蜿門ｾ励↓菴ｿ縺・
    implementation(libs.play.services.auth)

    // 隱ｲ驥・(雋ｷ縺・・繧翫・繝励Ξ繝溘い繝隗｣髯､)
    implementation(libs.billing.ktx)

    // 蠑慕畑縺ｮ繧ｫ繝｡繝ｩ蜿悶ｊ霎ｼ縺ｿ (譌･譛ｬ隱朧CR)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
