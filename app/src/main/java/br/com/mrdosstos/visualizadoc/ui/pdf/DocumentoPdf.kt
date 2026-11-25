package br.com.mrdosstos.visualizadoc.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.Closeable
import java.io.File

/**
 * Envolve o PdfRenderer nativo do Android.
 * O PdfRenderer não é thread-safe: todo acesso passa por [trava].
 */
class DocumentoPdf private constructor(
    private val descritor: ParcelFileDescriptor,
    private val arquivoTemporario: File?,
) : Closeable {

    private val trava = Any()
    private val renderer = PdfRenderer(descritor)
    private var fechado = false

    val totalPaginas: Int = renderer.pageCount

    /** Proporção altura/largura de cada página, preenchida conforme renderiza. */
    private val proporcoes = FloatArray(totalPaginas) { PROPORCAO_A4 }

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun proporcao(indice: Int): Float = proporcoes[indice]

    /** Renderiza a página na largura pedida. Chamar fora da thread principal. */
    fun renderizar(indice: Int, larguraPx: Int): Bitmap? {
        val chave = "$indice-$larguraPx"
        cache.get(chave)?.let { return it }
        synchronized(trava) {
            if (fechado) return null
            renderer.openPage(indice).use { pagina ->
                val proporcao = pagina.height.toFloat() / pagina.width
                proporcoes[indice] = proporcao
                val bitmap = Bitmap.createBitmap(
                    larguraPx,
                    (larguraPx * proporcao).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(chave, bitmap)
                return bitmap
            }
        }
    }

    override fun close() {
        synchronized(trava) {
            if (fechado) return
            fechado = true
            renderer.close()
            descritor.close()
            arquivoTemporario?.delete()
        }
        cache.evictAll()
    }

    companion object {
        private const val PROPORCAO_A4 = 1.4142f

        /**
         * Abre o PDF direto pela Uri. Se o provedor não entregar um descritor
         * "seekable" (exigido pelo PdfRenderer), copia para o cache e abre a cópia.
         * Lança SecurityException se o PDF for protegido por senha.
         */
        fun abrir(context: Context, uri: Uri): DocumentoPdf {
            val direto = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Descritor nulo")
                try {
                    DocumentoPdf(pfd, null)
                } catch (e: SecurityException) {
                    pfd.close()
                    throw e
                } catch (e: Exception) {
                    pfd.close()
                    null
                }
            }
            direto.exceptionOrNull()?.let { if (it is SecurityException) throw it }
            direto.getOrNull()?.let { return it }

            val copia = File.createTempFile("pdf_", ".pdf", context.cacheDir)
            context.contentResolver.openInputStream(uri).use { entrada ->
                requireNotNull(entrada) { "Não foi possível ler o arquivo" }
                copia.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            val pfd = ParcelFileDescriptor.open(copia, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                DocumentoPdf(pfd, copia)
            } catch (e: Exception) {
                pfd.close()
                copia.delete()
                throw e
            }
        }
    }
}
