plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versão: o workflow de release passa -PversionName (da tag) e -PversionCode.
val versaoNome = (findProperty("versionName") as String?) ?: "0.1.0"
val versaoCodigo = (findProperty("versionCode") as String?)?.toInt() ?: 1

// Assinatura de release: lida de variáveis de ambiente (Secrets do GitHub).
// Sem elas (build local), o release é assinado com a chave de debug.
val keystoreArquivo: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "br.com.mrdosstos.visualizadoc"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.mrdosstos.visualizadoc"
        minSdk = 26
        targetSdk = 36
        versionCode = versaoCodigo
        versionName = versaoNome
    }

    signingConfigs {
        if (keystoreArquivo != null) {
            create("release") {
                storeFile = file(keystoreArquivo)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
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
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
