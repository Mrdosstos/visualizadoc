package br.com.mrdosstos.visualizadoc.ui.texto

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.Codificacao
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.Gravacao
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.nio.charset.Charset

sealed interface EstadoTexto {
    data object Carregando : EstadoTexto
    data class Erro(val mensagem: String) : EstadoTexto
    data object Pronto : EstadoTexto
}

/** Estado do arquivo .txt aberto: leitura, edição e gravação na mesma codificação. */
class EditorTexto(
    documentoInicial: DocumentoAberto,
    private val app: Application,
    private val escopo: CoroutineScope,
    private val onDocumentoSalvo: (DocumentoAberto) -> Unit,
) {
    var documento by mutableStateOf(documentoInicial)
        private set
    var estado by mutableStateOf<EstadoTexto>(EstadoTexto.Carregando)
        private set
    /** Linhas para exibir (a leitura usa lista, que aguenta arquivos grandes). */
    var linhas by mutableStateOf(emptyList<String>())
        private set
    var editando by mutableStateOf(false)
        private set
    var texto by mutableStateOf("")
    var modificado by mutableStateOf(false)
        private set
    var salvando by mutableStateOf(false)
        private set
    /** Arquivos grandes demais para a caixa de edição ficam só para leitura. */
    var somenteLeitura by mutableStateOf(false)
        private set

    private var charset: Charset = Charsets.UTF_8
    private var comBom = false
    private var original = ""

    init {
        escopo.launch {
            try {
                val lido = withContext(Dispatchers.IO) {
                    val bytes = app.contentResolver.openInputStream(documento.uri)?.use { it.readBytes() }
                        ?: throw FileNotFoundException("Não foi possível ler o arquivo")
                    Codificacao.decodificar(bytes)
                }
                charset = lido.charset
                comBom = lido.comBom
                original = lido.texto
                texto = lido.texto
                linhas = lido.texto.lines()
                somenteLeitura = lido.texto.length > LIMITE_EDICAO
                estado = EstadoTexto.Pronto
            } catch (_: OutOfMemoryError) {
                estado = EstadoTexto.Erro("O arquivo é grande demais para abrir neste aparelho.")
            } catch (e: Exception) {
                estado = EstadoTexto.Erro("Não foi possível abrir o arquivo.\n${e.message ?: ""}")
            }
        }
    }

    fun alternarEdicao(): String? {
        if (somenteLeitura) return "Arquivo grande demais para editar no celular (mais de 300 mil caracteres)."
        if (editando) {
            linhas = texto.lines()
        }
        editando = !editando
        return null
    }

    fun aoDigitar(novo: String) {
        texto = novo
        modificado = novo != original
    }

    suspend fun salvar(destino: Uri = documento.uri): ResultadoSalvar {
        salvando = true
        return try {
            val bytes = Codificacao.codificar(texto, charset, comBom)
            withContext(Dispatchers.IO) { Gravacao.abrirSaida(app, destino).use { it.write(bytes) } }
            original = texto
            modificado = false
            linhas = texto.lines()
            if (destino != documento.uri) {
                documento = withContext(Dispatchers.IO) { ArquivoUtils.lerInfo(app, destino) }
            }
            onDocumentoSalvo(documento)
            ResultadoSalvar.Ok
        } catch (_: SecurityException) {
            ResultadoSalvar.PrecisaSalvarComo
        } catch (_: FileNotFoundException) {
            ResultadoSalvar.PrecisaSalvarComo
        } catch (_: UnsupportedOperationException) {
            ResultadoSalvar.PrecisaSalvarComo
        } catch (e: Exception) {
            ResultadoSalvar.Erro("Não foi possível salvar: ${e.message}")
        } finally {
            salvando = false
        }
    }

    private companion object {
        const val LIMITE_EDICAO = 300_000
    }
}
