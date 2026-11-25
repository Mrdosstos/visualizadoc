package br.com.mrdosstos.visualizadoc.planilha

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvTest {

    @Test fun detectaPontoEVirgula() =
        assertEquals(';', Csv.detectarSeparador("nome;idade;cidade\nAna;30;SP"))

    @Test fun detectaVirgula() = assertEquals(',', Csv.detectarSeparador("a,b,c\n1,2,3"))

    @Test fun ignoraSeparadorEntreAspas() =
        assertEquals(';', Csv.detectarSeparador("\"a,b,c,d\";x;y\n"))

    @Test fun analisaAspasEQuebraDeLinha() {
        val texto = "nome;obs\r\n\"Silva, Ana\";\"linha 1\nlinha 2\"\r\n\"diz \"\"oi\"\"\";\r\n"
        val linhas = Csv.analisar(texto, ';')
        assertEquals(3, linhas.size)
        assertEquals(listOf("Silva, Ana", "linha 1\nlinha 2"), linhas[1])
        assertEquals(listOf("diz \"oi\"", ""), linhas[2])
    }

    @Test fun idaEVoltaMantemFormato() {
        val original = "﻿nome;valor\r\nAna;\"1;2\"\r\n".toByteArray(Charsets.UTF_8)
        val (linhas, formato) = Csv.ler(original)
        assertEquals(';', formato.separador)
        assertEquals(true, formato.comBom)
        assertEquals("\r\n", formato.quebraLinha)

        val saida = ByteArrayOutputStream()
        Csv.escrever(linhas, formato, saida)
        assertArrayEquals(original, saida.toByteArray())
    }

    @Test fun leWindows1252() {
        val bytes = "nome;cidade\nJoão;São Paulo\n".toByteArray(charset("windows-1252"))
        val (linhas, formato) = Csv.ler(bytes)
        assertEquals("windows-1252", formato.charset.name())
        assertEquals(listOf("João", "São Paulo"), linhas[1])
    }
}
