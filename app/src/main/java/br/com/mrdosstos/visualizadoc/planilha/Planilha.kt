package br.com.mrdosstos.visualizadoc.planilha

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.formula.FormulaParseException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FormatoPlanilha(val mime: String, val extensao: String) {
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    XLS("application/vnd.ms-excel", "xls"),
    CSV("text/csv", "csv");

    companion object {
        fun detectar(nomeArquivo: String, mime: String?): FormatoPlanilha {
            val ext = nomeArquivo.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensao == ext }
                ?: when (mime) {
                    XLS.mime -> XLS
                    "text/csv", "text/comma-separated-values" -> CSV
                    else -> XLSX
                }
        }
    }
}

enum class Alinhamento { ESQUERDA, CENTRO, DIREITA }

/** O que a tela precisa para desenhar uma célula. */
data class CelulaVisual(
    val texto: String,
    val negrito: Boolean = false,
    val corFundo: Int? = null,
    val alinhamento: Alinhamento = Alinhamento.ESQUERDA,
) {
    companion object {
        val VAZIA = CelulaVisual("")
    }
}

/**
 * Planilha aberta em memória (xlsx, xls ou csv), sobre o Apache POI.
 * Não é thread-safe: use sempre a partir de uma única thread por vez.
 */
class Planilha private constructor(
    private val workbook: Workbook,
    val formato: FormatoPlanilha,
    private val formatoCsv: FormatoCsv?,
    private val locale: Locale,
) : Closeable {

    private val formatador = DataFormatter(locale)
    private val avaliador: FormulaEvaluator? =
        runCatching { workbook.creationHelper.createFormulaEvaluator() }.getOrNull()
    private val dimensoesCache = mutableMapOf<Int, Pair<Int, Int>>()

    val nomesAbas: List<String>
        get() = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }

    /** Quantidade de linhas e colunas usadas na aba. */
    fun dimensoes(aba: Int): Pair<Int, Int> = dimensoesCache.getOrPut(aba) {
        val sheet = workbook.getSheetAt(aba)
        if (sheet.physicalNumberOfRows == 0) return@getOrPut 0 to 0
        var colunas = 0
        sheet.forEach { row -> colunas = maxOf(colunas, row.lastCellNum.toInt()) }
        (sheet.lastRowNum + 1) to colunas
    }

    /** Largura da coluna em "caracteres" (unidade do Excel). */
    fun larguraColuna(aba: Int, coluna: Int): Float =
        runCatching { workbook.getSheetAt(aba).getColumnWidth(coluna) / 256f }.getOrDefault(8.43f)

    fun celula(aba: Int, linha: Int, coluna: Int): CelulaVisual {
        val cell = celulaPoi(aba, linha, coluna) ?: return CelulaVisual.VAZIA
        val texto = textoExibido(cell)
        if (formato == FormatoPlanilha.CSV) return CelulaVisual(texto)
        return runCatching {
            val estilo = cell.cellStyle
            val fonte = workbook.getFontAt(estilo.fontIndex)
            val numerico = cell.cellType == CellType.NUMERIC ||
                (cell.cellType == CellType.FORMULA && cell.cachedFormulaResultType == CellType.NUMERIC)
            val alinhamento = when (estilo.alignment) {
                HorizontalAlignment.CENTER, HorizontalAlignment.CENTER_SELECTION -> Alinhamento.CENTRO
                HorizontalAlignment.RIGHT -> Alinhamento.DIREITA
                HorizontalAlignment.LEFT -> Alinhamento.ESQUERDA
                else -> if (numerico) Alinhamento.DIREITA else Alinhamento.ESQUERDA
            }
            CelulaVisual(texto, fonte.bold, corDeFundo(cell), alinhamento)
        }.getOrDefault(CelulaVisual(texto))
    }

    /** Valor como o usuário edita: fórmula com "=", número sem milhar, data dd/MM/aaaa. */
    fun valorParaEdicao(aba: Int, linha: Int, coluna: Int): String {
        val cell = celulaPoi(aba, linha, coluna) ?: return ""
        return when (cell.cellType) {
            CellType.FORMULA -> "=" + cell.cellFormula
            CellType.NUMERIC ->
                if (DateUtil.isCellDateFormatted(cell)) formatarData(cell.dateCellValue)
                else Numeros.formatarParaEdicao(cell.numericCellValue, locale)
            CellType.BOOLEAN -> if (cell.booleanCellValue) "VERDADEIRO" else "FALSO"
            CellType.STRING -> cell.stringCellValue
            else -> formatador.formatCellValue(cell)
        }
    }

    /**
     * Grava o que o usuário digitou. "=..." vira fórmula, números seguem o idioma
     * do aparelho, e o resto vira texto. Em CSV tudo é texto.
     * @throws IllegalArgumentException se a fórmula for inválida.
     */
    fun definir(aba: Int, linha: Int, coluna: Int, entrada: String) {
        val sheet = workbook.getSheetAt(aba)
        val row = sheet.getRow(linha) ?: sheet.createRow(linha)
        val cell = row.getCell(coluna) ?: row.createCell(coluna)
        val eraData = cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
        val data = if (eraData) interpretarData(entrada) else null

        when {
            entrada.isEmpty() -> cell.setBlank()
            formato == FormatoPlanilha.CSV -> cell.setCellValue(entrada)
            entrada.startsWith("=") && entrada.length > 1 -> try {
                cell.cellFormula = entrada.substring(1)
            } catch (e: FormulaParseException) {
                throw IllegalArgumentException("Fórmula inválida: ${e.message}", e)
            }
            data != null -> cell.setCellValue(data)
            else -> {
                val numero = Numeros.interpretar(entrada, locale)
                if (numero != null) cell.setCellValue(numero) else cell.setCellValue(entrada)
            }
        }
        avaliador?.clearAllCachedResultValues()
        dimensoesCache.remove(aba)
    }

    fun salvar(saida: OutputStream) {
        if (formato == FormatoPlanilha.CSV) {
            val sheet = workbook.getSheetAt(0)
            val (linhas, colunas) = dimensoes(0)
            val dados = (0 until linhas).map { l ->
                (0 until colunas).map { c -> sheet.getRow(l)?.getCell(c)?.let { textoExibido(it) } ?: "" }
            }
            Csv.escrever(dados, requireNotNull(formatoCsv), saida)
        } else {
            // Pede ao Excel/LibreOffice para recalcular as fórmulas ao abrir.
            workbook.forceFormulaRecalculation = true
            workbook.write(saida)
        }
    }

    override fun close() = workbook.close()

    fun nomeColuna(coluna: Int): String = CellReference.convertNumToColString(coluna)

    // ---------------------------------------------------------------------

    private fun celulaPoi(aba: Int, linha: Int, coluna: Int): Cell? =
        workbook.getSheetAt(aba).getRow(linha)?.getCell(coluna)

    private fun textoExibido(cell: Cell): String {
        if (cell.cellType != CellType.FORMULA) return formatador.formatCellValue(cell)
        return try {
            formatador.formatCellValue(cell, avaliador)
        } catch (_: Exception) {
            // Função não suportada pelo POI: mostra o último valor calculado pelo Excel.
            when (cell.cachedFormulaResultType) {
                CellType.NUMERIC -> formatador.formatRawCellContents(
                    cell.numericCellValue, cell.cellStyle.dataFormat.toInt(), cell.cellStyle.dataFormatString
                )
                CellType.STRING -> cell.richStringCellValue.string
                CellType.BOOLEAN -> if (cell.booleanCellValue) "VERDADEIRO" else "FALSO"
                CellType.ERROR -> FormulaError.forInt(cell.errorCellValue).string
                else -> ""
            }
        }
    }

    /** Cor de preenchimento sólido como ARGB, ou null. */
    private fun corDeFundo(cell: Cell): Int? {
        val estilo = cell.cellStyle
        if (estilo.fillPattern != FillPatternType.SOLID_FOREGROUND) return null
        val rgb: ByteArray? = when (val cor = estilo.fillForegroundColorColor) {
            is XSSFColor -> cor.rgbWithTint ?: cor.rgb
            is HSSFColor -> cor.triplet.map { it.toByte() }.toByteArray()
            else -> null
        }
        if (rgb == null || rgb.size < 3) return null
        val r = rgb[rgb.size - 3].toInt() and 0xFF
        val g = rgb[rgb.size - 2].toInt() and 0xFF
        val b = rgb[rgb.size - 1].toInt() and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun formatarData(data: Date): String {
        val padrao = SimpleDateFormat("HH:mm:ss", locale).format(data)
        return if (padrao == "00:00:00") SimpleDateFormat("dd/MM/yyyy", locale).format(data)
        else SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(data)
    }

    private fun interpretarData(texto: String): Date? {
        for (padrao in listOf("dd/MM/yyyy HH:mm", "dd/MM/yyyy")) {
            try {
                val formato = SimpleDateFormat(padrao, locale).apply { isLenient = false }
                return formato.parse(texto.trim())
            } catch (_: ParseException) {
            }
        }
        return null
    }

    companion object {
        fun abrir(
            entrada: InputStream,
            formato: FormatoPlanilha,
            locale: Locale = Locale.getDefault(),
        ): Planilha = when (formato) {
            FormatoPlanilha.CSV -> {
                val (linhas, formatoCsv) = Csv.ler(entrada.readBytes())
                val wb = XSSFWorkbook()
                val sheet = wb.createSheet("CSV")
                linhas.forEachIndexed { l, campos ->
                    val row = sheet.createRow(l)
                    campos.forEachIndexed { c, valor ->
                        if (valor.isNotEmpty()) row.createCell(c).setCellValue(valor)
                    }
                }
                Planilha(wb, formato, formatoCsv, locale)
            }
            else -> {
                val wb = WorkbookFactory.create(entrada)
                val formatoReal = if (wb is HSSFWorkbook) FormatoPlanilha.XLS else FormatoPlanilha.XLSX
                Planilha(wb, formatoReal, null, locale)
            }
        }
    }
}
