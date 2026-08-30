plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.luoluo.reminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.luoluo.reminder"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "2.6"
    }

    buildTypes {
        debug {
            // 开源版本的应用名
            resValue("string", "app_name", "今天也要努力生活呀！加油鸭！")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 使用 debug 签名，方便直接安装体验
            signingConfig = signingConfigs.getByName("debug")
            // 作者自用版：应用名留空白，通知头部不显示名字
            resValue("string", "app_name", " ")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
