package br.com.mrdosstos.visualizadoc.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import java.io.File

/**
 * Lista os documentos guardados no aparelho, pelo índice do sistema (MediaStore).
 * Exige "Acesso a todos os arquivos" (Android 11+) ou permissão de leitura (Android 8–10).
 */
object ArquivosDoAparelho {

    fun temPermissao(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Tela do sistema onde o usuário liga o acesso a todos os arquivos (Android 11+). */
    fun intentPermissao(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun listar(context: Context): List<ItemArquivo> {
        val colecao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        @Suppress("DEPRECATION")
        val colunaCaminho = MediaStore.MediaColumns.DATA
        val projecao = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            colunaCaminho,
        )
        val selecao = TipoDocumento.EXTENSOES.joinToString(" OR ") { "$colunaCaminho LIKE ?" }
        val argumentos = TipoDocumento.EXTENSOES.map { "%.$it" }.toTypedArray()

        val itens = mutableListOf<ItemArquivo>()
        context.contentResolver.query(colecao, projecao, selecao, argumentos, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iNome = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iTamanho = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iCaminho = c.getColumnIndexOrThrow(colunaCaminho)
            while (c.moveToNext()) {
                val caminho = c.getString(iCaminho) ?: continue
                // Pula pastas ocultas e de sistema (ex.: /Android/data, .thumbnails).
                if (caminho.split('/').any { it.startsWith(".") } || "/Android/" in caminho) continue
                val nome = c.getString(iNome) ?: File(caminho).name
                val tamanho = c.getLong(iTamanho)
                if (tamanho <= 0L) continue
                itens += ItemArquivo(
                    uri = ContentUris.withAppendedId(colecao, c.getLong(iId)).toString(),
                    nome = nome,
                    tipo = TipoDocumento.detectar(null, nome),
                    tamanho = tamanho,
                    data = c.getLong(iData) * 1000L,
                )
            }
        }
        return itens
    }

    /** Apaga o arquivo do aparelho. Retorna true se conseguiu. */
    fun excluir(context: Context, uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrDefault(false)
}
