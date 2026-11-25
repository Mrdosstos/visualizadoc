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

private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

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
                val falhaAoCriar = {
                    Toast.makeText(this, "Não foi possível criar o arquivo.", Toast.LENGTH_LONG).show()
                }
                val novoWord = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(MIME_DOCX)
                ) { uri -> if (uri != null) vm.criarNovo(uri, TipoDocumento.WORD, falhaAoCriar) }
                val novaPlanilha = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(MIME_XLSX)
                ) { uri -> if (uri != null) vm.criarNovo(uri, TipoDocumento.PLANILHA, falhaAoCriar) }

                val documento = vm.documentoAberto
                val editor = vm.editorPlanilha
                val editorWord = vm.editorDocumento
                val editorPdf = vm.editorPdf
                val voltar = { vm.fechar() }
                when {
                    documento == null -> HomeScreen(
                        recentes = vm.recentes,
                        onAbrirArquivo = { tipo -> seletor.launch(TipoDocumento.mimesDe(tipo)) },
                        onAbrirRecente = { abrir(it.uri.toUri(), manterPermissao = false) },
                        onRemoverRecente = { vm.removerRecente(it.uri) },
                        onNovo = { tipo ->
                            if (tipo == TipoDocumento.PLANILHA) novaPlanilha.launch("Planilha.xlsx")
                            else novoWord.launch("Documento.docx")
                        },
                    )
                    editor != null -> PlanilhaScreen(editor, voltar)
                    editorWord != null -> DocumentoScreen(editorWord, voltar) { convertido ->
                        abrir(convertido, manterPermissao = true)
                    }
                    editorPdf != null -> PdfScreen(editorPdf, voltar)
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
