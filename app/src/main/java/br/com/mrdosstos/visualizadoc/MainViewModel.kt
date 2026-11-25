package br.com.mrdosstos.visualizadoc

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.RecentesRepository
import br.com.mrdosstos.visualizadoc.ui.documento.EditorDocumento
import br.com.mrdosstos.visualizadoc.ui.planilha.EditorPlanilha

/** Estado da tela principal; sobrevive à rotação do aparelho. */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RecentesRepository(app)

    var recentes by mutableStateOf(repo.listar())
        private set
    var documentoAberto by mutableStateOf<DocumentoAberto?>(null)
        private set
    var editorPlanilha by mutableStateOf<EditorPlanilha?>(null)
        private set
    var editorDocumento by mutableStateOf<EditorDocumento?>(null)
        private set

    /** Abre o documento. Retorna false se não for possível acessar o arquivo. */
    fun abrir(uri: Uri, manterPermissao: Boolean): Boolean {
        val app = getApplication<Application>()
        if (manterPermissao) ArquivoUtils.manterPermissao(app, uri)
        val documento = try {
            ArquivoUtils.lerInfo(app, uri)
        } catch (_: Exception) {
            return false
        }
        fechar()
        registrarRecente(documento)
        documentoAberto = documento
        if (documento.tipo == TipoDocumento.PLANILHA) {
            editorPlanilha = EditorPlanilha(documento, app, viewModelScope, ::registrarRecente)
        }
        // .doc (formato binário antigo) ainda não é suportado; só .docx.
        if (documento.tipo == TipoDocumento.WORD && !documento.nome.lowercase().endsWith(".doc")) {
            editorDocumento = EditorDocumento(documento, app, viewModelScope, ::registrarRecente)
        }
        return true
    }

    fun fechar() {
        editorPlanilha?.close()
        editorPlanilha = null
        editorDocumento?.close()
        editorDocumento = null
        documentoAberto = null
    }

    fun removerRecente(uri: String) {
        repo.remover(uri)
        recentes = repo.listar()
    }

    private fun registrarRecente(documento: DocumentoAberto) {
        repo.registrar(documento)
        recentes = repo.listar()
    }

    override fun onCleared() {
        editorPlanilha?.close()
        editorDocumento?.close()
    }
}
