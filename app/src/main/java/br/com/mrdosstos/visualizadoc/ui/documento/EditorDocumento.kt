package br.com.mrdosstos.visualizadoc.ui.documento

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.Gravacao
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import br.com.mrdosstos.visualizadoc.documento.Bloco
import br.com.mrdosstos.visualizadoc.documento.DocumentoWord
import br.com.mrdosstos.visualizadoc.documento.Formato
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileNotFoundException

sealed interface EstadoDocumento {
    data object Carregando : EstadoDocumento
    data class Erro(val mensagem: String) : EstadoDocumento
    data object Pronto : EstadoDocumento
}

/**
 * Estado do documento Word aberto: blocos para a tela, parágrafo em edição e gravação.
 * O POI não é thread-safe; leitura e gravação do arquivo passam por uma fila única,
 * e as edições acontecem na thread principal (são rápidas).
 */
class EditorDocumento(
    documentoInicial: DocumentoAberto,
    private val app: Application,
    private val escopo: CoroutineScope,
    private val onDocumentoSalvo: (DocumentoAberto) -> Unit,
) : Closeable {

    var documento by mutableStateOf(documentoInicial)
        private set
    var estado by mutableStateOf<EstadoDocumento>(EstadoDocumento.Carregando)
        private set
    var blocos by mutableStateOf(emptyList<Bloco>())
        private set
    /** Índice (no corpo do documento) do parágrafo em edição, ou null. */
    var editando by mutableStateOf<Int?>(null)
        private set
    var textoEdicao by mutableStateOf("")
    var modificado by mutableStateOf(false)
        private set
    var salvando by mutableStateOf(false)
        private set

    private var word: DocumentoWord? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fila = Dispatchers.IO.limitedParallelism(1)

    init {
        escopo.launch {
            try {
                val aberto = withContext(fila) {
                    app.contentResolver.openInputStream(documento.uri)?.use { DocumentoWord.abrir(it) }
                        ?: throw FileNotFoundException("Não foi possível ler o arquivo")
                }
                word = aberto
                blocos = aberto.blocos()
                estado = EstadoDocumento.Pronto
            } catch (_: OutOfMemoryError) {
                estado = EstadoDocumento.Erro("O documento é grande demais para abrir neste aparelho.")
            } catch (e: Exception) {
                estado = EstadoDocumento.Erro("Não foi possível abrir o documento.\n${e.message ?: ""}")
            }
        }
    }

    fun paragrafoEmEdicao(): Bloco.Paragrafo? =
        editando?.let { i -> blocos.firstOrNull { it is Bloco.Paragrafo && it.indice == i } as? Bloco.Paragrafo }

    fun formatoAtivo(formato: Formato): Boolean {
        val trechos = paragrafoEmEdicao()?.trechos.orEmpty()
        return trechos.isNotEmpty() && trechos.all {
            when (formato) {
                Formato.NEGRITO -> it.negrito
                Formato.ITALICO -> it.italico
                Formato.SUBLINHADO -> it.sublinhado
            }
        }
    }

    /** Começa a editar um parágrafo (gravando antes o que estava em edição). */
    fun editar(paragrafo: Bloco.Paragrafo): String? {
        if (!paragrafo.editavel || salvando) return null
        val erro = concluirEdicao()
        // Os índices não mudam ao gravar texto, então o parágrafo continua válido.
        editando = paragrafo.indice
        textoEdicao = blocos.filterIsInstance<Bloco.Paragrafo>()
            .firstOrNull { it.indice == paragrafo.indice }?.texto ?: paragrafo.texto
        return erro
    }

    /** Grava o texto em edição e sai do modo de edição. */
    fun concluirEdicao(): String? {
        val erro = gravarTextoPendente()
        editando = null
        return erro
    }

    fun alternarFormato(formato: Formato): String? {
        val i = editando ?: return null
        return aplicar {
            gravarTexto(i)
            alternarFormato(i, formato)
        }
    }

    fun definirTitulo(nivel: Int): String? {
        val i = editando ?: return null
        return aplicar {
            gravarTexto(i)
            definirTitulo(i, nivel)
        }
    }

    fun novoParagrafoAbaixo(): String? {
        val i = editando ?: return null
        var novo = -1
        val erro = aplicar {
            gravarTexto(i)
            novo = inserirParagrafoDepois(i)
        }
        if (erro == null && novo >= 0) {
            editando = novo
            textoEdicao = ""
        }
        return erro
    }

    fun excluirParagrafo(): String? {
        val i = editando ?: return null
        editando = null
        return aplicar { removerParagrafo(i) }
    }

    fun editarCelula(indiceTabela: Int, linha: Int, coluna: Int, texto: String): String? {
        concluirEdicao()?.let { return it }
        return aplicar { editarCelula(indiceTabela, linha, coluna, texto) }
    }

    suspend fun salvar(destino: Uri = documento.uri): ResultadoSalvar {
        concluirEdicao()?.let { return ResultadoSalvar.Erro(it) }
        val w = word ?: return ResultadoSalvar.Erro("O documento ainda não foi carregado.")
        salvando = true
        return try {
            // Gera o arquivo inteiro antes de abrir o destino, para não deixá-lo pela metade.
            val bytes = withContext(fila) { ByteArrayOutputStream().also { w.salvar(it) }.toByteArray() }
            withContext(Dispatchers.IO) { Gravacao.abrirSaida(app, destino).use { it.write(bytes) } }
            modificado = false
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

    override fun close() {
        runCatching { word?.close() }
        word = null
    }

    // ---------------------------------------------------------------------

    private fun gravarTextoPendente(): String? {
        val i = editando ?: return null
        val atual = paragrafoEmEdicao()?.texto
        if (atual == textoEdicao) return null
        return aplicar { gravarTexto(i) }
    }

    private fun DocumentoWord.gravarTexto(indice: Int) {
        val atual = blocos.filterIsInstance<Bloco.Paragrafo>().firstOrNull { it.indice == indice }?.texto
        if (atual != textoEdicao) editarTexto(indice, textoEdicao)
    }

    /** Executa a alteração no documento, atualiza os blocos e marca como modificado. */
    private fun aplicar(acao: DocumentoWord.() -> Unit): String? {
        val w = word ?: return "O documento ainda não foi carregado."
        if (salvando) return "Aguarde terminar de salvar."
        return try {
            w.acao()
            blocos = w.blocos()
            modificado = true
            null
        } catch (e: Exception) {
            "Não foi possível alterar o documento: ${e.message}"
        }
    }
}
