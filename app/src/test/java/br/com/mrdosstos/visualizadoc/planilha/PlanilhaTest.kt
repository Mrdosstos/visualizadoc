package br.com.mrdosstos.visualizadoc.planilha

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale

class PlanilhaTest {
    private val ptBR = Locale.forLanguageTag("pt-BR")

    @Before fun configurarPoi() = PoiAndroid.configurar()

    private fun criar(wb: Workbook): ByteArray {
        val sheet = wb.createSheet("Dados")
        sheet.createRow(0).apply {
            createCell(0).setCellValue("Produto")
            createCell(1).setCellValue("Preço")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("Caneta")
            createCell(1).setCellValue(2.5)
        }
        wb.createSheet("Outra")
        return ByteArrayOutputStream().also { wb.write(it); wb.close() }.toByteArray()
    }

    private fun abrir(bytes: ByteArray, formato: FormatoPlanilha) =
        Planilha.abrir(ByteArrayInputStream(bytes), formato, ptBR)

    @Test fun leXlsx() {
        abrir(criar(XSSFWorkbook()), FormatoPlanilha.XLSX).use { p ->
            assertEquals(listOf("Dados", "Outra"), p.nomesAbas)
            assertEquals(2 to 2, p.dimensoes(0))
            assertEquals("Produto", p.celula(0, 0, 0).texto)
            assertEquals("2,5", p.valorParaEdicao(0, 1, 1))
        }
    }

    @Test fun editaFormulaESalvaXlsx() {
        val salvo = abrir(criar(XSSFWorkbook()), FormatoPlanilha.XLSX).use { p ->
            p.definir(0, 1, 2, "=B2*4")
            assertEquals("10", p.celula(0, 1, 2).texto)
            p.definir(0, 1, 1, "3,5")
            assertEquals("14", p.celula(0, 1, 2).texto)
            p.definir(0, 2, 0, "Lápis")
            ByteArrayOutputStream().also { p.salvar(it) }.toByteArray()
        }
        abrir(salvo, FormatoPlanilha.XLSX).use { p ->
            assertEquals("=B2*4", p.valorParaEdicao(0, 1, 2))
            assertEquals("14", p.celula(0, 1, 2).texto)
            assertEquals("Lápis", p.celula(0, 2, 0).texto)
            assertEquals(3 to 3, p.dimensoes(0))
        }
    }

    @Test fun editaESalvaXls() {
        val salvo = abrir(criar(HSSFWorkbook()), FormatoPlanilha.XLS).use { p ->
            assertEquals(FormatoPlanilha.XLS, p.formato)
            p.definir(0, 0, 0, "Item")
            ByteArrayOutputStream().also { p.salvar(it) }.toByteArray()
        }
        abrir(salvo, FormatoPlanilha.XLS).use { p ->
            assertEquals("Item", p.celula(0, 0, 0).texto)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun formulaInvalida() {
        abrir(criar(XSSFWorkbook()), FormatoPlanilha.XLSX).use { it.definir(0, 0, 0, "=SOMA((") }
    }

    @Test fun csvMantemTextoComoEsta() {
        val csv = "nome;cep\nAna;01234-000\n".toByteArray()
        val salvo = abrir(csv, FormatoPlanilha.CSV).use { p ->
            assertEquals("01234-000", p.celula(0, 1, 1).texto)
            p.definir(0, 1, 0, "Ana Maria")
            p.definir(0, 2, 0, "Bia")
            ByteArrayOutputStream().also { p.salvar(it) }.toByteArray()
        }
        assertEquals("nome;cep\nAna Maria;01234-000\nBia;\n", String(salvo))
    }

    @Test fun detectaFormato() {
        assertEquals(FormatoPlanilha.XLS, FormatoPlanilha.detectar("a.XLS", null))
        assertEquals(FormatoPlanilha.CSV, FormatoPlanilha.detectar("dados", "text/csv"))
        assertEquals(FormatoPlanilha.XLSX, FormatoPlanilha.detectar("x.xlsx", null))
    }
}
