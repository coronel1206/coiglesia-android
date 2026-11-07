import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.coiglesia.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.coiglesia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
        resValue("string", "app_name", "Coiglesia")

        val supabaseUrl =
            System.getenv("SUPABASE_URL") ?: ""

        val supabaseKey =
            System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""

        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
        require(!supabaseKey.startsWith("sb_secret_")) { "No se permiten claves administrativas en Android" }
        if (supabaseKey.startsWith("eyJ")) {
            val payload = runCatching { String(Base64.getUrlDecoder().decode(supabaseKey.split('.')[1])) }.getOrDefault("")
            require(!payload.contains("service_role")) { "No se permite service_role en Android" }
        }
        buildConfigField("String", "SUPABASE_URL", quoted(supabaseUrl))

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            quoted(supabaseKey)
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-pruebas"
            resValue("string", "app_name", "Coiglesia Pruebas")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
