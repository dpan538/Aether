import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val signingProperties=Properties().apply{rootProject.file(".tooling/signing/keystore.properties").takeIf{it.exists()}?.inputStream()?.use{load(it)}}
dependencyLocking { lockAllConfigurations() }
android {
    namespace = "com.aether.radio"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.dpan538.aether"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.gradleProperty("aetherVersionCode").orElse("4").get().toInt()
        versionName = providers.gradleProperty("aetherVersionName").orElse("0.1.0-preview.3").get()
        testInstrumentationRunner = "com.aether.radio.NativeEvidence"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    testBuildType=providers.gradleProperty("aetherTestBuildType").orElse("verification").get()
    signingConfigs {if(signingProperties.isNotEmpty())create("aetherPreview"){
        storeFile=rootProject.file(signingProperties.getProperty("storeFile"))
        storePassword=signingProperties.getProperty("storePassword")
        keyAlias=signingProperties.getProperty("keyAlias")
        keyPassword=signingProperties.getProperty("keyPassword")
    }}
    buildTypes {
        getByName("debug") {applicationIdSuffix=".debug"}
        getByName("release") {isMinifyEnabled=false;if(signingProperties.isNotEmpty())signingConfig=signingConfigs.getByName("aetherPreview")}
        create("verification") {initWith(getByName("release"));isDebuggable=true;matchingFallbacks+=listOf("debug")}
    }
}
kotlin {compilerOptions {jvmTarget.set(JvmTarget.JVM_17)}}
dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("com.android.tools.build:apksig:8.13.2")
    testImplementation("junit:junit:4.13.2")
}
