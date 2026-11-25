package br.com.mrdosstos.visualizadoc.data

import android.content.Context
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import org.json.JSONArray
import org.json.JSONObject

data class ArquivoRecente(
    val uri: String,
    val nome: String,
    val tipo: TipoDocumento,
    val abertoEm: Long,
)

/** Guarda a lista dos últimos arquivos abertos em SharedPreferences (JSON). */
class RecentesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("recentes", Context.MODE_PRIVATE)

    fun listar(): List<ArquivoRecente> {
        val json = prefs.getString(CHAVE_LISTA, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ArquivoRecente(
                    uri = obj.getString("uri"),
                    nome = obj.getString("nome"),
                    tipo = runCatching { TipoDocumento.valueOf(obj.getString("tipo")) }
                        .getOrDefault(TipoDocumento.DESCONHECIDO),
                    abertoEm = obj.getLong("abertoEm"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun registrar(documento: DocumentoAberto) {
        val uri = documento.uri.toString()
        val novo = ArquivoRecente(uri, documento.nome, documento.tipo, System.currentTimeMillis())
        salvar(listOf(novo) + listar().filterNot { it.uri == uri })
    }

    fun remover(uri: String) {
        salvar(listar().filterNot { it.uri == uri })
    }

    private fun salvar(lista: List<ArquivoRecente>) {
        val array = JSONArray()
        lista.take(MAXIMO).forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("nome", it.nome)
                    .put("tipo", it.tipo.name)
                    .put("abertoEm", it.abertoEm)
            )
        }
        prefs.edit().putString(CHAVE_LISTA, array.toString()).apply()
    }

    private companion object {
        const val CHAVE_LISTA = "lista"
        const val MAXIMO = 30
    }
}
