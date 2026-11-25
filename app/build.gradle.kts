import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versão: o workflow de release passa -PversionName (da tag) e -PversionCode.
val versaoNome = (findProperty("versionName") as String?) ?: "0.7.0"
val versaoCodigo = (findProperty("versionCode") as String?)?.toInt() ?: 7

// Assinatura de release: lida de variáveis de ambiente (Secrets do GitHub).
// Sem elas (build local), o release é assinado com a chave de debug.
val keystoreArquivo: String? = System.getenv("KEYSTORE_FILE")

// Apache POI adaptado para Android, gerado pelo módulo :poishadow.
val poiJar = files("../poishadow/build/libs/poishadow-all.jar")
    .builtBy(":poishadow:shadowJar")

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
            // R8 só remove código e recursos não usados (sem otimizar nem renomear,
            // que travavam o build com o Apache POI). Ver proguard-rules.pro.
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
        // O Apache POI usa APIs do Java mais novas que algumas versões do Android.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // A análise de release travava com o POI; o lint segue disponível via ./gradlew lint.
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all { teste ->
            teste.testLogging {
                events("passed", "failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }
            teste.timeout.set(Duration.ofMinutes(5))
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(poiJar)
    // Edição e busca de texto em PDF (Apache 2.0).
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
