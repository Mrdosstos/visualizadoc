package br.com.mrdosstos.visualizadoc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.ui.EmBreveScreen
import br.com.mrdosstos.visualizadoc.ui.documento.DocumentoScreen
import br.com.mrdosstos.visualizadoc.ui.home.HomeScreen
import br.com.mrdosstos.visualizadoc.ui.pdf.PdfScreen
import br.com.mrdosstos.visualizadoc.ui.planilha.PlanilhaScreen
import br.com.mrdosstos.visualizadoc.ui.theme.VisualizaDocTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) tratarIntent(intent)

        setContent {
            VisualizaDocTheme {
                val seletor = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) abrir(uri, manterPermissao = true) }

                val documento = vm.documentoAberto
                val editor = vm.editorPlanilha
                val editorWord = vm.editorDocumento
                val voltar = { vm.fechar() }
                when {
                    documento == null -> HomeScreen(
                        recentes = vm.recentes,
                        onAbrirArquivo = { tipo -> seletor.launch(TipoDocumento.mimesDe(tipo)) },
                        onAbrirRecente = { abrir(it.uri.toUri(), manterPermissao = false) },
                        onRemoverRecente = { vm.removerRecente(it.uri) },
                    )
                    editor != null -> PlanilhaScreen(editor, voltar)
                    editorWord != null -> DocumentoScreen(editorWord, voltar)
                    documento.tipo == TipoDocumento.PDF -> {
                        BackHandler(onBack = voltar)
                        PdfScreen(documento, voltar)
                    }
                    else -> {
                        BackHandler(onBack = voltar)
                        EmBreveScreen(documento, voltar)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratarIntent(intent)
    }

    /** Arquivo vindo de "Abrir com" de outro app. */
    private fun tratarIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { abrir(it, manterPermissao = false) }
        }
    }

    private fun abrir(uri: Uri, manterPermissao: Boolean) {
        if (!vm.abrir(uri, manterPermissao)) {
            Toast.makeText(
                this,
                "Não foi possível acessar o arquivo. Abra-o novamente pelo botão \"Abrir arquivo\".",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
