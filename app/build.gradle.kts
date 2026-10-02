import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase (avisos al instante, FCM): `google-services.json` (proyecto cuentiva-eedb9, paquete com.cuadra.caja) vive fuera de git. Sin él (CI, otra persona)
// la app compila igual: el plugin no se aplica, Firebase no se inicia y los teléfonos se ponen al día con la sincronización frecuente.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) apply(plugin = "com.google.gms.google-services")

base {
    // Nombre de los archivos que salen: cuentiva-0.2.0-release.apk / .aab
    archivesName.set("cuentiva-0.2.0")
}

android {
    namespace = "com.cuadra.caja"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cuadra.caja"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        // Idiomas empaquetados: la app no se traduce a otros aunque alguna librería los incluya.
        androidResources.localeFilters += listOf("es", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // `direct` = APK firmado propio; `play` = Google Play (afecta donaciones y suscripción, PLAN.md 8.3).
        buildConfigField("String", "DISTRIBUTION", "\"${providers.gradleProperty("DISTRIBUTION").getOrElse("direct")}\"")
        // Cliente OAuth "Web" de Google (el mismo que acepta la API en GOOGLE_CLIENT_IDS) y URL de la API.
        // Se pasan con -PGOOGLE_WEB_CLIENT_ID=... y -PCUADRA_API_URL=...; sin ellos la app compila pero el acceso con Google avisa que falta configurar.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").getOrElse("")}\"")
        buildConfigField("String", "API_URL", "\"${providers.gradleProperty("CUADRA_API_URL").getOrElse("http://10.0.2.2:8086")}\"") // depuración: emulador; release: ver abajo
        // ¿Se compiló con la configuración de Firebase? (sin ella no se intenta registrar el token).
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
    }

    // Firma de release: keystore/release.properties (no versionado). Sin él se firma con la clave debug.
    val releaseProps = rootProject.file("keystore/release.properties")
    if (releaseProps.exists()) {
        val props = Properties().apply { releaseProps.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // La versión de producción habla con la API pública por https (se puede cambiar con -PCUADRA_API_URL=…).
            buildConfigField("String", "API_URL", "\"${providers.gradleProperty("CUADRA_API_URL").getOrElse("https://cuadra-backend-production.up.railway.app")}\"")
            // El lector de códigos (ML Kit) trae librerías nativas por arquitectura: en producción solo teléfonos (ARM); x86 queda para depuración/emulador.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseProps.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Todo texto visible va en recursos (es/en). Este chequeo cubre los layouts XML; en Compose lo vigila
        // la prueba de paridad de cadenas y la revisión (PLAN.md 7.2).
        error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    }

    // Las pruebas de migración leen los esquemas exportados de Room.
    sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g"; it.maxParallelForks = 4 }
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // El esquema de Room se versiona: cada migración se prueba contra el esquema anterior.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.googleid)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bcrypt)
    // Lector de códigos de barras: CameraX + ML Kit con el modelo incluido en la app (funciona sin conexión).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    // Avisos al instante: mensajes de datos «sincroniza ya» y avisos de la bandeja (ver `data/push`).
    implementation(libs.firebase.messaging)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Guardia de diseño (ui/guard): renderiza las pantallas en la JVM con Robolectric y revisa recortes según letra/ancho/idioma.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
