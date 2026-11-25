package br.com.mrdosstos.visualizadoc.data

import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import java.text.Collator
import java.util.Locale

/** Um arquivo nas listas da tela inicial (do aparelho, recente ou favorito). */
data class ItemArquivo(
    val uri: String,
    val nome: String,
    val tipo: TipoDocumento,
    /** Tamanho em bytes, quando conhecido. */
    val tamanho: Long?,
    /** Data de modificação (arquivos do aparelho) ou de abertura (recentes), em ms. */
    val data: Long,
)

enum class CriterioOrdem { DATA, NOME, TAMANHO }

data class Ordenacao(val criterio: CriterioOrdem = CriterioOrdem.DATA, val crescente: Boolean = false)

object Ordenador {
    fun ordenar(lista: List<ItemArquivo>, ordem: Ordenacao, locale: Locale = Locale.getDefault()): List<ItemArquivo> {
        val comparador: Comparator<ItemArquivo> = when (ordem.criterio) {
            CriterioOrdem.DATA -> compareBy { it.data }
            CriterioOrdem.TAMANHO -> compareBy { it.tamanho ?: -1L }
            CriterioOrdem.NOME -> {
                val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
                Comparator { a, b -> collator.compare(a.nome, b.nome) }
            }
        }
        return lista.sortedWith(if (ordem.crescente) comparador else comparador.reversed())
    }
}
