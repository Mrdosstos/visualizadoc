package br.com.mrdosstos.visualizadoc.ui.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.Gravacao
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import br.com.mrdosstos.visualizadoc.pdf.Ocorrencia
import br.com.mrdosstos.visualizadoc.pdf.PdfEditavel
import br.com.mrdosstos.visualizadoc.pdf.TrechoPdf
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

sealed interface EstadoPdf {
    data object Carregando : EstadoPdf
    data class Erro(val mensagem: String) : EstadoPdf
    data object Pronto : EstadoPdf
}

/**
 * Estado do PDF aberto. A tela desenha uma cópia de trabalho no cache; cada
 * edição gera uma nova cópia, que passa a ser desenhada. "Salvar" grava a cópia
 * atual no arquivo do usuário.
 */
class EditorPdf(
    documentoInicial: DocumentoAberto,
    private val app: Application,
    private val escopo: CoroutineScope,
    private val onDocumentoSalvo: (DocumentoAberto) -> Unit,
) : Closeable {

    var documento by mutableStateOf(documentoInicial)
        private set
    var estado by mutableStateOf<EstadoPdf>(EstadoPdf.Carregando)
        private set
    /** Cópia que a tela desenha. Muda a cada edição. */
    var arquivoRender by mutableStateOf<File?>(null)
        private set
    /** Muda quando os trechos de texto mudam (para redesenhar as marcações). */
    var versaoTrechos by mutableIntStateOf(0)
        private set
    var modoEdicao by mutableStateOf(false)
        private set
    var preparandoTexto by mutableStateOf(false)
        private set
    var modificado by mutableStateOf(false)
        private set
    var salvando by mutableStateOf(false)
        private set

    var buscaAberta by mutableStateOf(false)
        private set
    var termoBusca by mutableStateOf("")
    var ocorrencias by mutableStateOf(emptyList<Ocorrencia>())
        private set
    var indiceOcorrencia by mutableIntStateOf(0)
        private set

    val ocorrenciaAtual: Ocorrencia? get() = ocorrencias.getOrNull(indiceOcorrencia)

    private val pasta = File(app.cacheDir, "pdf-trabalho/${UUID.randomUUID()}")
    private var pdf: PdfEditavel? = null
    private var copias = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fila = Dispatchers.IO.limitedParallelism(1)

    init {
        escopo.launch {
            try {
                arquivoRender = withContext(fila) {
                    pasta.mkdirs()
                    val original = File(pasta, "copia-0.pdf")
                    app.contentResolver.openInputStream(documento.uri)?.use { entrada ->
                        original.outputStream().use { entrada.copyTo(it) }
                    } ?: throw FileNotFoundException("Não foi possível ler o arquivo")
                    original
                }
                estado = EstadoPdf.Pronto
            } catch (e: Exception) {
                estado = EstadoPdf.Erro("Não foi possível abrir o PDF.\n${e.message ?: ""}")
            }
        }
    }

    /** Trechos de texto da página (vazio se o texto ainda não foi preparado). */
    suspend fun trechos(pagina: Int): List<TrechoPdf> {
        val p = pdf ?: return emptyList()
        return withContext(fila) { runCatching { p.trechos(pagina) }.getOrDefault(emptyList()) }
    }

    /** Liga/desliga o modo de edição. Retorna uma mensagem se não for possível editar. */
    suspend fun alternarModoEdicao(): String? {
        if (modoEdicao) {
            modoEdicao = false
            return null
        }
        val p = prepararTexto() ?: return mensagemTexto
        if (!p.podeEditar) return "Este PDF está protegido contra alterações."
        modoEdicao = true
        buscaAberta = false
        return null
    }

    /** Substitui o texto do trecho e atualiza a cópia desenhada. Retorna aviso ou null. */
    suspend fun editar(pagina: Int, indice: Int, novoTexto: String): String? {
        val p = pdf ?: return "O texto do PDF ainda não foi preparado."
        salvando = true
        return try {
            val (faltando, novaCopia) = withContext(fila) {
                val faltando = p.substituir(pagina, indice, novoTexto)
                copias++
                val nova = File(pasta, "copia-$copias.pdf")
                p.salvar(nova)
                // Mantém a original (lida pela PdfBox) e a anterior (a tela pode estar desenhando dela).
                if (copias - 2 >= 1) File(pasta, "copia-${copias - 2}.pdf").delete()
                faltando to nova
            }
            arquivoRender = novaCopia
            versaoTrechos++
            modificado = true
            if (faltando.isEmpty()) null
            else "Alguns caracteres não existem na fonte e viraram \"?\": ${faltando.joinToString(" ")}"
        } catch (e: IllegalArgumentException) {
            e.message
        } catch (e: Exception) {
            "Não foi possível editar: ${e.message}"
        } finally {
            salvando = false
        }
    }

    fun abrirBusca() {
        buscaAberta = true
        modoEdicao = false
    }

    fun fecharBusca() {
        buscaAberta = false
        ocorrencias = emptyList()
        indiceOcorrencia = 0
        versaoTrechos++
    }

    /** Busca o termo no PDF. Retorna uma mensagem para mostrar, ou null. */
    suspend fun buscar(): String? {
        val p = prepararTexto() ?: return mensagemTexto
        val termo = termoBusca
        ocorrencias = withContext(fila) { p.buscar(termo) }
        indiceOcorrencia = 0
        versaoTrechos++
        return if (ocorrencias.isEmpty()) "Nada encontrado para \"$termo\"." else null
    }

    fun proximaOcorrencia() {
        if (ocorrencias.isNotEmpty()) indiceOcorrencia = (indiceOcorrencia + 1) % ocorrencias.size
    }

    fun ocorrenciaAnterior() {
        if (ocorrencias.isNotEmpty()) indiceOcorrencia = (indiceOcorrencia - 1 + ocorrencias.size) % ocorrencias.size
    }

    /** Grava a cópia atual no arquivo original (padrão) ou no destino do "Salvar como". */
    suspend fun salvar(destino: Uri = documento.uri): ResultadoSalvar {
        val copia = arquivoRender ?: return ResultadoSalvar.Erro("O PDF ainda não foi carregado.")
        salvando = true
        return try {
            withContext(Dispatchers.IO) {
                val bytes = copia.readBytes()
                Gravacao.abrirSaida(app, destino).use { it.write(bytes) }
            }
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
        val p = pdf
        pdf = null
        escopo.launch(fila) {
            runCatching { p?.close() }
            pasta.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------

    private var mensagemTexto: String? = null

    /** Abre o PDF com a PdfBox (só na primeira vez que edição ou busca são usadas). */
    private suspend fun prepararTexto(): PdfEditavel? {
        pdf?.let { return it }
        val original = File(pasta, "copia-0.pdf")
        preparandoTexto = true
        return try {
            withContext(fila) { PdfEditavel.abrir(original) }.also { pdf = it }
        } catch (_: InvalidPasswordException) {
            mensagemTexto = "Este PDF é protegido por senha."
            null
        } catch (_: OutOfMemoryError) {
            mensagemTexto = "O PDF é grande demais para editar ou buscar neste aparelho."
            null
        } catch (e: Exception) {
            mensagemTexto = "Não foi possível ler o texto do PDF: ${e.message}"
            null
        } finally {
            preparandoTexto = false
        }
    }
}
