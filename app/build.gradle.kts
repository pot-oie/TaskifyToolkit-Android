plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.taskifyapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.taskifyapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // 启用 viewBinding
    buildFeatures {
        viewBinding = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // 核心依赖
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    // 内置 HTTP 服务器（用于接收指令）
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Retrofit 用于网络请求 (用于上报结果)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson 转换器，用于将对象序列化为 JSON
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // OkHttp 日志拦截器，方便在 Logcat 中查看网络请求的详细信息
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}