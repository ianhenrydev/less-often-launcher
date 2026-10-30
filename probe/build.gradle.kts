plugins { id("com.android.application") }
android {
    namespace = "com.lessoften.probe"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.lessoften.probe"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.lessoften.probe.ProbeInstrumentation"
    }
    sourceSets.getByName("androidTest").assets.srcDir("../app/src/main/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
