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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.RecentesRepository
import br.com.mrdosstos.visualizadoc.ui.EmBreveScreen
import br.com.mrdosstos.visualizadoc.ui.home.HomeScreen
import br.com.mrdosstos.visualizadoc.ui.pdf.PdfScreen
import br.com.mrdosstos.visualizadoc.ui.theme.VisualizaDocTheme

class MainActivity : ComponentActivity() {

    private lateinit var recentesRepo: RecentesRepository
    private var documentoAberto by mutableStateOf<DocumentoAberto?>(null)
    private var recentes by mutableStateOf(emptyList<br.com.mrdosstos.visualizadoc.data.ArquivoRecente>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recentesRepo = RecentesRepository(this)
        recentes = recentesRepo.listar()
        if (savedInstanceState == null) tratarIntent(intent)

        setContent {
            VisualizaDocTheme {
                val seletor = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) abrir(uri, manterPermissao = true) }

                val documento = documentoAberto
                if (documento == null) {
                    HomeScreen(
                        recentes = recentes,
                        onAbrirArquivo = { seletor.launch(TipoDocumento.MIMES_SUPORTADOS) },
                        onAbrirRecente = { abrir(it.uri.toUri(), manterPermissao = false) },
                        onRemoverRecente = {
                            recentesRepo.remover(it.uri)
                            recentes = recentesRepo.listar()
                        },
                    )
                } else {
                    BackHandler { documentoAberto = null }
                    val voltar = { documentoAberto = null }
                    when (documento.tipo) {
                        TipoDocumento.PDF -> PdfScreen(documento, voltar)
                        else -> EmBreveScreen(documento, voltar)
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
        if (manterPermissao) ArquivoUtils.manterPermissao(this, uri)
        val documento = try {
            ArquivoUtils.lerInfo(this, uri)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Não foi possível acessar o arquivo. Abra-o novamente pelo botão \"Abrir arquivo\".",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        recentesRepo.registrar(documento)
        recentes = recentesRepo.listar()
        documentoAberto = documento
    }
}
