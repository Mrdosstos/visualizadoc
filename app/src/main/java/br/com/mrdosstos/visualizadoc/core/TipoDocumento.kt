package br.com.mrdosstos.visualizadoc.core

/** Tipos de documento que o app reconhece. */
enum class TipoDocumento(val sigla: String, val descricao: String) {
    PDF("PDF", "Documento PDF"),
    PLANILHA("XLS", "Planilha"),
    WORD("DOC", "Documento Word"),
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

        /** MIME types oferecidos no seletor de arquivos do sistema. */
        val MIMES_SUPORTADOS: Array<String> =
            (MIMES_PDF + MIMES_PLANILHA + MIMES_WORD).toTypedArray()

        /** MIME types de um tipo só, para abrir o seletor já filtrado. */
        fun mimesDe(tipo: TipoDocumento?): Array<String> = when (tipo) {
            PDF -> MIMES_PDF.toTypedArray()
            PLANILHA -> MIMES_PLANILHA.toTypedArray()
            WORD -> MIMES_WORD.toTypedArray()
            else -> MIMES_SUPORTADOS
        }

        /** Detecta o tipo pelo MIME e, se não bater, pela extensão do nome. */
        fun detectar(mime: String?, nomeArquivo: String): TipoDocumento {
            val extensao = nomeArquivo.substringAfterLast('.', "").lowercase()
            return when {
                mime in MIMES_PDF || extensao == "pdf" -> PDF
                mime in MIMES_PLANILHA || extensao in setOf("xlsx", "xls", "csv") -> PLANILHA
                mime in MIMES_WORD || extensao in setOf("docx", "doc") -> WORD
                else -> DESCONHECIDO
            }
        }
    }
}
