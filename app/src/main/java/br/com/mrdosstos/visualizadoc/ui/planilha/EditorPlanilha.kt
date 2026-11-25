package br.com.mrdosstos.visualizadoc.ui.planilha

import android.app.Application
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.planilha.CelulaVisual
import br.com.mrdosstos.visualizadoc.planilha.FormatoPlanilha
import br.com.mrdosstos.visualizadoc.planilha.Planilha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.util.CellReference
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.OutputStream

sealed interface EstadoPlanilha {
    data object Carregando : EstadoPlanilha
    data class Erro(val mensagem: String) : EstadoPlanilha
    data class Pronta(val abas: List<String>, val formato: FormatoPlanilha) : EstadoPlanilha
}

sealed interface ResultadoSalvar {
    data object Ok : ResultadoSalvar
    /** Sem permissão de escrita no arquivo original: oferecer "Salvar como". */
    data object PrecisaSalvarComo : ResultadoSalvar
    data class Erro(val mensagem: String) : ResultadoSalvar
}

/**
 * Estado da planilha aberta: carregamento, seleção, edição e gravação.
 * O POI não é thread-safe; leitura e gravação do arquivo passam por uma fila única.
 */
class EditorPlanilha(
    documentoInicial: DocumentoAberto,
    private val app: Application,
    private val escopo: CoroutineScope,
    private val onDocumentoSalvo: (DocumentoAberto) -> Unit,
) : Closeable {

    var documento by mutableStateOf(documentoInicial)
        private set
    var estado by mutableStateOf<EstadoPlanilha>(EstadoPlanilha.Carregando)
        private set
    var abaAtual by mutableIntStateOf(0)
        private set
    var selecao by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var textoEdicao by mutableStateOf("")
    /** Muda a cada edição, para a grade redesenhar as células. */
    var versao by mutableIntStateOf(0)
        private set
    var modificado by mutableStateOf(false)
        private set
    var salvando by mutableStateOf(false)
        private set

    private var planilha: Planilha? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fila = Dispatchers.IO.limitedParallelism(1)
    private val cache = LruCache<Long, CelulaVisual>(4000)

    val formato: FormatoPlanilha
        get() = (estado as? EstadoPlanilha.Pronta)?.formato
            ?: FormatoPlanilha.detectar(documento.nome, null)

    init {
        carregar()
    }

    private fun carregar() = escopo.launch {
        try {
            val formato = FormatoPlanilha.detectar(documento.nome, app.contentResolver.getType(documento.uri))
            val aberta = withContext(fila) {
                app.contentResolver.openInputStream(documento.uri)?.use { Planilha.abrir(it, formato) }
                    ?: throw FileNotFoundException("Não foi possível ler o arquivo")
            }
            planilha = aberta
            estado = EstadoPlanilha.Pronta(aberta.nomesAbas, aberta.formato)
        } catch (_: OutOfMemoryError) {
            estado = EstadoPlanilha.Erro("A planilha é grande demais para abrir neste aparelho.")
        } catch (e: Exception) {
            estado = EstadoPlanilha.Erro("Não foi possível abrir a planilha.\n${e.message ?: ""}")
        }
    }

    /** Linhas e colunas mostradas: as usadas, mais uma folga para digitar dados novos. */
    fun dimensoesExibicao(): Pair<Int, Int> {
        val (linhas, colunas) = planilha?.dimensoes(abaAtual) ?: (0 to 0)
        return maxOf(linhas + 50, 100) to (colunas + 5).coerceIn(26, MAX_COLUNAS)
    }

    fun larguraColunaDp(coluna: Int): Float =
        ((planilha?.larguraColuna(abaAtual, coluna) ?: 8.43f) * 7.5f).coerceIn(40f, 320f)

    fun nomeColuna(coluna: Int): String = CellReference.convertNumToColString(coluna)

    fun celula(linha: Int, coluna: Int): CelulaVisual {
        val chave = (abaAtual.toLong() shl 40) or (linha.toLong() shl 16) or coluna.toLong()
        cache.get(chave)?.let { return it }
        val visual = runCatching { planilha?.celula(abaAtual, linha, coluna) }.getOrNull()
            ?: CelulaVisual.VAZIA
        cache.put(chave, visual)
        return visual
    }

    fun selecionarAba(indice: Int) {
        abaAtual = indice
        selecao = null
        textoEdicao = ""
    }

    fun selecionar(linha: Int, coluna: Int) {
        selecao = linha to coluna
        textoEdicao = runCatching { planilha?.valorParaEdicao(abaAtual, linha, coluna) }.getOrNull() ?: ""
    }

    /** Grava o texto da barra na célula selecionada e desce uma linha. Retorna erro ou null. */
    fun confirmarEdicao(): String? {
        val (linha, coluna) = selecao ?: return null
        val p = planilha ?: return null
        if (salvando) return "Aguarde terminar de salvar."
        return try {
            p.definir(abaAtual, linha, coluna, textoEdicao)
            cache.evictAll()
            versao++
            modificado = true
            selecionar(linha + 1, coluna)
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "Valor inválido."
        } catch (e: Exception) {
            "Não foi possível alterar a célula: ${e.message}"
        }
    }

    /** Salva no arquivo original (padrão) ou no destino escolhido em "Salvar como". */
    suspend fun salvar(destino: Uri = documento.uri): ResultadoSalvar {
        val p = planilha ?: return ResultadoSalvar.Erro("A planilha ainda não foi carregada.")
        salvando = true
        return try {
            // Gera o arquivo inteiro antes de abrir o destino, para não deixá-lo pela metade.
            val bytes = withContext(fila) { ByteArrayOutputStream().also { p.salvar(it) }.toByteArray() }
            withContext(Dispatchers.IO) { abrirSaida(destino).use { it.write(bytes) } }
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

    private fun abrirSaida(uri: Uri): OutputStream {
        val resolver = app.contentResolver
        return try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } ?: throw FileNotFoundException("Destino indisponível")
    }

    override fun close() {
        runCatching { planilha?.close() }
        planilha = null
    }

    companion object {
        const val MAX_COLUNAS = 300
    }
}
