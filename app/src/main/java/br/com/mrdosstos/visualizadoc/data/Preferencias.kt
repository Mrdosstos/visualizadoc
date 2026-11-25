package br.com.mrdosstos.visualizadoc.data

import android.content.Context
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import org.json.JSONArray
import org.json.JSONObject

enum class Tema { SISTEMA, CLARO, ESCURO }

/** Escolhas do usuário que ficam salvas: tema e ordenação das listas. */
class Preferencias(context: Context) {
    private val prefs = context.getSharedPreferences("preferencias", Context.MODE_PRIVATE)

    var tema: Tema
        get() = runCatching { Tema.valueOf(prefs.getString("tema", null) ?: "") }.getOrDefault(Tema.SISTEMA)
        set(valor) = prefs.edit().putString("tema", valor.name).apply()

    var ordenacao: Ordenacao
        get() = Ordenacao(
            criterio = runCatching { CriterioOrdem.valueOf(prefs.getString("ordem_criterio", null) ?: "") }
                .getOrDefault(CriterioOrdem.DATA),
            crescente = prefs.getBoolean("ordem_crescente", false),
        )
        set(valor) = prefs.edit()
            .putString("ordem_criterio", valor.criterio.name)
            .putBoolean("ordem_crescente", valor.crescente)
            .apply()
}

/** Arquivos marcados com estrela, salvos em SharedPreferences (JSON). */
class FavoritosRepository(context: Context) {
    private val prefs = context.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun listar(): List<ItemArquivo> {
        val json = prefs.getString("lista", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ItemArquivo(
                    uri = o.getString("uri"),
                    nome = o.getString("nome"),
                    tipo = runCatching { TipoDocumento.valueOf(o.getString("tipo")) }.getOrDefault(TipoDocumento.DESCONHECIDO),
                    tamanho = if (o.has("tamanho")) o.getLong("tamanho") else null,
                    data = o.getLong("data"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Liga/desliga a estrela. Retorna true se ficou favorito. */
    fun alternar(item: ItemArquivo): Boolean {
        val atual = listar()
        val era = atual.any { it.uri == item.uri }
        salvar(if (era) atual.filterNot { it.uri == item.uri } else listOf(item) + atual)
        return !era
    }

    fun remover(uris: Set<String>) = salvar(listar().filterNot { it.uri in uris })

    private fun salvar(lista: List<ItemArquivo>) {
        val array = JSONArray()
        lista.forEach {
            array.put(
                JSONObject().put("uri", it.uri).put("nome", it.nome).put("tipo", it.tipo.name)
                    .put("data", it.data).apply { it.tamanho?.let { t -> put("tamanho", t) } }
            )
        }
        prefs.edit().putString("lista", array.toString()).apply()
    }
}
