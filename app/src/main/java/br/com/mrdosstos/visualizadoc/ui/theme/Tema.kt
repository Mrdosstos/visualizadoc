package br.com.mrdosstos.visualizadoc.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AzulPrincipal = Color(0xFF1565C0)

private val EsquemaClaro = lightColorScheme(primary = AzulPrincipal)
private val EsquemaEscuro = darkColorScheme(primary = Color(0xFF90CAF9))

/** Tema do app: usa as cores dinâmicas do sistema (Android 12+) quando disponíveis. */
@Composable
fun VisualizaDocTheme(content: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    val context = LocalContext.current
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        escuro -> EsquemaEscuro
        else -> EsquemaClaro
    }
    MaterialTheme(colorScheme = esquema, content = content)
}
