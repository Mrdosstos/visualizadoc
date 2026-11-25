package br.com.mrdosstos.visualizadoc

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.Gravacao
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.ArquivosDoAparelho
import br.com.mrdosstos.visualizadoc.data.FavoritosRepository
import br.com.mrdosstos.visualizadoc.data.ItemArquivo
import br.com.mrdosstos.visualizadoc.data.Ordenacao
import br.com.mrdosstos.visualizadoc.data.Preferencias
import br.com.mrdosstos.visualizadoc.data.RecentesRepository
import br.com.mrdosstos.visualizadoc.data.Tema
import br.com.mrdosstos.visualizadoc.documento.DocumentoWord
import br.com.mrdosstos.visualizadoc.planilha.Planilha
import br.com.mrdosstos.visualizadoc.ui.apresentacao.EditorApresentacao
import br.com.mrdosstos.visualizadoc.ui.documento.EditorDocumento
import br.com.mrdosstos.visualizadoc.ui.pdf.EditorPdf
import br.com.mrdosstos.visualizadoc.ui.planilha.EditorPlanilha
import br.com.mrdosstos.visualizadoc.ui.texto.EditorTexto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resultado de tentar abrir um arquivo. */
sealed interface ResultadoAbrir {
    data object Aberto : ResultadoAbrir
    data object Falha : ResultadoAbrir
    /** Tipo que o app não exibe (PowerPoint): abrir em outro app. */
    data class AbrirFora(val uri: Uri, val mime: String) : ResultadoAbrir
}

/** Estado da tela principal; sobrevive à rotação do aparelho. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RecentesRepository(app)
    private val favoritosRepo = FavoritosRepository(app)
    private val preferencias = Preferencias(app)

    var recentes by mutableStateOf(repo.listar())
        private set
    var favoritos by mutableStateOf(favoritosRepo.listar())
        private set
    var arquivosDoAparelho by mutableStateOf(emptyList<ItemArquivo>())
        private set
    var temPermissaoArquivos by mutableStateOf(false)
        private set
    var carregandoArquivos by mutableStateOf(false)
        private set

    var tema by mutableStateOf(preferencias.tema)
        private set
    var ordenacao by mutableStateOf(preferencias.ordenacao)
        private set

    var documentoAberto by mutableStateOf<DocumentoAberto?>(null)
        private set
    var editorPlanilha by mutableStateOf<EditorPlanilha?>(null)
        private set
    var editorDocumento by mutableStateOf<EditorDocumento?>(null)
        private set
    var editorPdf by mutableStateOf<EditorPdf?>(null)
        private set
    var editorTexto by mutableStateOf<EditorTexto?>(null)
        private set
    var editorApresentacao by mutableStateOf<EditorApresentacao?>(null)
        private set

    /** Recentes no mesmo formato das outras listas (sem tamanho; data = quando foi aberto). */
    val recentesComoItens: List<ItemArquivo>
        get() = recentes.map { ItemArquivo(it.uri, it.nome, it.tipo, null, it.abertoEm) }

    /** Confere a permissão e (re)carrega a lista de documentos do aparelho. */
    fun atualizarArquivos() {
        val app = getApplication<Application>()
        temPermissaoArquivos = ArquivosDoAparelho.temPermissao(app)
        if (!temPermissaoArquivos || carregandoArquivos) return
        carregandoArquivos = true
        viewModelScope.launch {
            arquivosDoAparelho = withContext(Dispatchers.IO) {
                runCatching { ArquivosDoAparelho.listar(app) }.getOrDefault(emptyList())
            }
            carregandoArquivos = false
        }
    }

    fun definirTema(novo: Tema) {
        preferencias.tema = novo
        tema = novo
    }

    fun definirOrdenacao(nova: Ordenacao) {
        preferencias.ordenacao = nova
        ordenacao = nova
    }

    fun alternarFavorito(item: ItemArquivo) {
        favoritosRepo.alternar(item)
        favoritos = favoritosRepo.listar()
    }

    /** Apaga os arquivos do aparelho. Retorna quantos foram apagados. */
    suspend fun excluir(itens: List<ItemArquivo>): Int {
        val app = getApplication<Application>()
        val apagados = withContext(Dispatchers.IO) {
            itens.filter { ArquivosDoAparelho.excluir(app, it.uri.toUri()) }.map { it.uri }.toSet()
        }
        apagados.forEach { repo.remover(it) }
        favoritosRepo.remover(apagados)
        recentes = repo.listar()
        favoritos = favoritosRepo.listar()
        arquivosDoAparelho = arquivosDoAparelho.filterNot { it.uri in apagados }
        return apagados.size
    }

    fun removerRecente(uri: String) {
        repo.remover(uri)
        recentes = repo.listar()
    }

    fun abrir(uri: Uri, manterPermissao: Boolean): ResultadoAbrir {
        val app = getApplication<Application>()
        if (manterPermissao) ArquivoUtils.manterPermissao(app, uri)
        val documento = try {
            ArquivoUtils.lerInfo(app, uri)
        } catch (_: Exception) {
            return ResultadoAbrir.Falha
        }
        fechar()
        registrarRecente(documento)
        documentoAberto = documento
        when (documento.tipo) {
            TipoDocumento.PLANILHA -> editorPlanilha = EditorPlanilha(documento, app, viewModelScope, ::registrarRecente)
            TipoDocumento.WORD -> editorDocumento = EditorDocumento(documento, app, viewModelScope, ::registrarRecente)
            TipoDocumento.PDF -> editorPdf = EditorPdf(documento, app, viewModelScope, ::registrarRecente)
            TipoDocumento.TEXTO -> editorTexto = EditorTexto(documento, app, viewModelScope, ::registrarRecente)
            TipoDocumento.APRESENTACAO -> editorApresentacao = EditorApresentacao(documento, app, viewModelScope)
            else -> Unit
        }
        return ResultadoAbrir.Aberto
    }

    fun fechar() {
        editorPlanilha?.close()
        editorPlanilha = null
        editorDocumento?.close()
        editorDocumento = null
        editorPdf?.close()
        editorPdf = null
        editorTexto = null
        editorApresentacao = null
        documentoAberto = null
        // Ao voltar para a lista, reflete arquivos salvos ou criados.
        atualizarArquivos()
    }

    /** Cria um .docx ou .xlsx em branco no local escolhido e abre. Avisa por [onErro] se falhar. */
    fun criarNovo(uri: Uri, tipo: TipoDocumento, onErro: () -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    Gravacao.abrirSaida(app, uri).use { saida ->
                        if (tipo == TipoDocumento.PLANILHA) Planilha.escreverEmBranco(saida)
                        else DocumentoWord.escreverEmBranco(saida)
                    }
                }.isSuccess
            }
            if (!ok || abrir(uri, manterPermissao = true) != ResultadoAbrir.Aberto) onErro()
        }
    }

    private fun registrarRecente(documento: DocumentoAberto) {
        repo.registrar(documento)
        recentes = repo.listar()
    }

    override fun onCleared() {
        editorPlanilha?.close()
        editorDocumento?.close()
        editorPdf?.close()
    }
}
