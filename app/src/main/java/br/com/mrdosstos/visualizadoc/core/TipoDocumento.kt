package br.com.mrdosstos.visualizadoc.core

/** Tipos de documento que o app reconhece. */
enum class TipoDocumento(val sigla: String, val descricao: String) {
    PDF("PDF", "Documento PDF"),
    PLANILHA("XLS", "Planilha"),
    WORD("DOC", "Documento Word"),
    /** PowerPoint: aparece na lista e abre em outro app. */
    APRESENTACAO("PPT", "Apresentação"),
    TEXTO("TXT", "Texto"),
    DESCONHECIDO("?", "Arquivo");

    companion object {
        private val MIMES_PDF = setOf("application/pdf")
        private val MIMES_PLANILHA = setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/csv",
            "text/comma-separated-values",
        )
        private val MIMES_WORD = setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
        )
        private val MIMES_APRESENTACAO = setOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
        )
        private val MIMES_TEXTO = setOf("text/plain")

        private val EXT_PDF = setOf("pdf")
        private val EXT_PLANILHA = setOf("xlsx", "xls", "csv")
        private val EXT_WORD = setOf("docx", "doc")
        private val EXT_APRESENTACAO = setOf("pptx", "ppt")
        private val EXT_TEXTO = setOf("txt")

        /** Extensões procuradas ao listar os documentos do aparelho. */
        val EXTENSOES: List<String> =
            (EXT_PDF + EXT_WORD + EXT_PLANILHA + EXT_APRESENTACAO + EXT_TEXTO).toList()

        /** MIME types oferecidos no seletor de arquivos do sistema (tipos que o app abre). */
        val MIMES_SUPORTADOS: Array<String> =
            (MIMES_PDF + MIMES_PLANILHA + MIMES_WORD + MIMES_TEXTO).toTypedArray()

        /** MIME types de um tipo só, para abrir o seletor já filtrado. */
        fun mimesDe(tipo: TipoDocumento?): Array<String> = when (tipo) {
            PDF -> MIMES_PDF.toTypedArray()
            PLANILHA -> MIMES_PLANILHA.toTypedArray()
            WORD -> MIMES_WORD.toTypedArray()
            APRESENTACAO -> MIMES_APRESENTACAO.toTypedArray()
            TEXTO -> MIMES_TEXTO.toTypedArray()
            else -> MIMES_SUPORTADOS
        }

        /** MIME principal de um nome de arquivo (para abrir em outro app ou compartilhar). */
        fun mimeDoNome(nomeArquivo: String): String =
            when (nomeArquivo.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "doc" -> "application/msword"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "xls" -> "application/vnd.ms-excel"
                "csv" -> "text/csv"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "ppt" -> "application/vnd.ms-powerpoint"
                "txt" -> "text/plain"
                else -> "*/*"
            }

        /**
         * Detecta o tipo pela extensão e, sem extensão conhecida, pelo MIME.
         * A extensão vem primeiro porque alguns apps mandam .csv como "text/plain".
         */
        fun detectar(mime: String?, nomeArquivo: String): TipoDocumento {
            val extensao = nomeArquivo.substringAfterLast('.', "").lowercase()
            return when {
                extensao in EXT_PDF -> PDF
                extensao in EXT_PLANILHA -> PLANILHA
                extensao in EXT_WORD -> WORD
                extensao in EXT_APRESENTACAO -> APRESENTACAO
                extensao in EXT_TEXTO -> TEXTO
                mime in MIMES_PDF -> PDF
                mime in MIMES_PLANILHA -> PLANILHA
                mime in MIMES_WORD -> WORD
                mime in MIMES_APRESENTACAO -> APRESENTACAO
                mime in MIMES_TEXTO -> TEXTO
                else -> DESCONHECIDO
            }
        }
    }
}
