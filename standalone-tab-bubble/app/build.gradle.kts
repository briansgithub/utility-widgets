plugins { id("com.android.application") }
android {
    namespace = "com.tabubble.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.tabubble.android"
        minSdk = 30
        targetSdk = 36
        versionCode = 8
        versionName = "0.8"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
