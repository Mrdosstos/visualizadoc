package br.com.mrdosstos.visualizadoc.planilha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class NumerosTest {
    private val ptBR = Locale.forLanguageTag("pt-BR")
    private val enUS = Locale.US

    @Test fun inteiro() = assertEquals(42.0, Numeros.interpretar("42", ptBR)!!, 0.0)
    @Test fun decimalComVirgula() = assertEquals(12.5, Numeros.interpretar("12,5", ptBR)!!, 0.0)
    @Test fun milharEDecimal() = assertEquals(1234.56, Numeros.interpretar("1.234,56", ptBR)!!, 1e-9)
    @Test fun milharSemDecimal() = assertEquals(1234567.0, Numeros.interpretar("1.234.567", ptBR)!!, 0.0)
    @Test fun negativo() = assertEquals(-3.25, Numeros.interpretar("-3,25", ptBR)!!, 0.0)
    @Test fun percentual() = assertEquals(0.5, Numeros.interpretar("50%", ptBR)!!, 1e-12)
    @Test fun espacosNasBordas() = assertEquals(7.0, Numeros.interpretar("  7 ", ptBR)!!, 0.0)

    @Test fun pontoAmbiguoViraTexto() = assertNull(Numeros.interpretar("1.5", ptBR))
    @Test fun duasVirgulas() = assertNull(Numeros.interpretar("1,2,3", ptBR))
    @Test fun texto() = assertNull(Numeros.interpretar("abc", ptBR))
    @Test fun vazio() = assertNull(Numeros.interpretar("", ptBR))
    @Test fun soSinal() = assertNull(Numeros.interpretar("-", ptBR))

    @Test fun formatoAmericano() = assertEquals(1234.56, Numeros.interpretar("1,234.56", enUS)!!, 1e-9)

    @Test fun formatarParaEdicao() {
        assertEquals("1234,5", Numeros.formatarParaEdicao(1234.5, ptBR))
        assertEquals("10", Numeros.formatarParaEdicao(10.0, ptBR))
        assertEquals("0,1", Numeros.formatarParaEdicao(0.1, ptBR))
        assertEquals("1234.5", Numeros.formatarParaEdicao(1234.5, enUS))
    }
}
