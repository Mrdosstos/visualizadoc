package br.com.mrdosstos.visualizadoc

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import br.com.mrdosstos.visualizadoc.core.Compartilhar
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.ArquivosDoAparelho
import br.com.mrdosstos.visualizadoc.ui.EmBreveScreen
import br.com.mrdosstos.visualizadoc.ui.apresentacao.ApresentacaoScreen
import br.com.mrdosstos.visualizadoc.ui.documento.DocumentoScreen
import br.com.mrdosstos.visualizadoc.ui.home.AcoesInicio
import br.com.mrdosstos.visualizadoc.ui.home.DadosInicio
import br.com.mrdosstos.visualizadoc.ui.home.HomeScreen
import br.com.mrdosstos.visualizadoc.ui.pdf.PdfScreen
import br.com.mrdosstos.visualizadoc.ui.planilha.PlanilhaScreen
import br.com.mrdosstos.visualizadoc.ui.texto.TextoScreen
import br.com.mrdosstos.visualizadoc.ui.theme.VisualizaDocTheme
import kotlinx.coroutines.launch

private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) tratarIntent(intent)

        val versao = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

        setContent {
            VisualizaDocTheme(tema = vm.tema) {
                val escopo = rememberCoroutineScope()
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
                // Android 8 a 10: permissão de leitura comum.
                val permissaoLeitura = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { vm.atualizarArquivos() }

                val documento = vm.documentoAberto
                val editorPlanilha = vm.editorPlanilha
                val editorWord = vm.editorDocumento
                val editorPdf = vm.editorPdf
                val editorTexto = vm.editorTexto
                val editorApresentacao = vm.editorApresentacao
                val voltar = { vm.fechar() }
                when {
                    documento == null -> HomeScreen(
                        dados = DadosInicio(
                            arquivosDoAparelho = vm.arquivosDoAparelho,
                            temPermissao = vm.temPermissaoArquivos,
                            carregando = vm.carregandoArquivos,
                            recentes = vm.recentesComoItens,
                            favoritos = vm.favoritos,
                            ordenacao = vm.ordenacao,
                            tema = vm.tema,
                            versao = versao,
                        ),
                        acoes = AcoesInicio(
                            abrir = { abrir(it.uri.toUri(), manterPermissao = false) },
                            abrirSeletor = { tipo -> seletor.launch(TipoDocumento.mimesDe(tipo)) },
                            novo = { tipo ->
                                if (tipo == TipoDocumento.PLANILHA) novaPlanilha.launch("Planilha.xlsx")
                                else novoWord.launch("Documento.docx")
                            },
                            pedirPermissao = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        startActivity(ArquivosDoAparelho.intentPermissao(this))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(
                                            this,
                                            "Abra Configurações > Apps > VisualizaDoc > Permissões.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                } else {
                                    permissaoLeitura.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            },
                            alternarFavorito = { vm.alternarFavorito(it) },
                            excluir = { itens ->
                                escopo.launch {
                                    val apagados = vm.excluir(itens)
                                    val msg = when {
                                        apagados == itens.size ->
                                            if (apagados == 1) "Arquivo excluído." else "$apagados arquivos excluídos."
                                        apagados == 0 -> "Não foi possível excluir. Verifique a permissão de acesso aos arquivos."
                                        else -> "$apagados de ${itens.size} arquivos excluídos."
                                    }
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            compartilhar = { itens -> Compartilhar.enviarVarios(this, itens.map { it.uri.toUri() }) },
                            removerRecente = { vm.removerRecente(it.uri) },
                            definirOrdenacao = { vm.definirOrdenacao(it) },
                            definirTema = { vm.definirTema(it) },
                            compartilharApp = { Compartilhar.indicarApp(this) },
                        ),
                    )
                    editorPlanilha != null -> PlanilhaScreen(editorPlanilha, voltar)
                    editorWord != null -> DocumentoScreen(editorWord, voltar) { convertido ->
                        abrir(convertido, manterPermissao = true)
                    }
                    editorPdf != null -> PdfScreen(editorPdf, voltar)
                    editorTexto != null -> TextoScreen(editorTexto, voltar)
                    editorApresentacao != null -> ApresentacaoScreen(editorApresentacao, voltar) {
                        val doc = editorApresentacao.documento
                        abrirEmOutroApp(doc.uri, TipoDocumento.mimeDoNome(doc.nome))
                    }
                    else -> {
                        BackHandler(onBack = voltar)
                        EmBreveScreen(documento, voltar)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar das configurações (permissão) ou de outro app, atualiza a lista.
        if (vm.documentoAberto == null) vm.atualizarArquivos()
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
        when (val r = vm.abrir(uri, manterPermissao)) {
            ResultadoAbrir.Aberto -> Unit
            ResultadoAbrir.Falha -> Toast.makeText(
                this,
                "Não foi possível acessar o arquivo. Abra-o novamente pelo botão \"Abrir outro arquivo\".",
                Toast.LENGTH_LONG,
            ).show()
            is ResultadoAbrir.AbrirFora -> abrirEmOutroApp(r.uri, r.mime)
        }
    }

    /** Abre o arquivo no app de apresentações do aparelho (layout original dos slides). */
    private fun abrirEmOutroApp(uri: Uri, mime: String) {
        val ver = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(ver, "Abrir apresentação com"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum app instalado abre apresentações.", Toast.LENGTH_LONG).show()
        }
    }
}
