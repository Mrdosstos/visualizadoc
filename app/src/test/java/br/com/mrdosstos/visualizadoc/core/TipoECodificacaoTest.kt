package br.com.mrdosstos.visualizadoc.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TipoECodificacaoTest {

    @Test fun detectaPelaExtensaoAntesDoMime() {
        assertEquals(TipoDocumento.PLANILHA, TipoDocumento.detectar("text/plain", "dados.csv"))
        assertEquals(TipoDocumento.TEXTO, TipoDocumento.detectar(null, "notas.TXT"))
        assertEquals(TipoDocumento.APRESENTACAO, TipoDocumento.detectar(null, "aula.pptx"))
        assertEquals(TipoDocumento.WORD, TipoDocumento.detectar(null, "antigo.doc"))
        assertEquals(TipoDocumento.PDF, TipoDocumento.detectar("application/pdf", "sem-extensao"))
        assertEquals(TipoDocumento.DESCONHECIDO, TipoDocumento.detectar(null, "foto.jpg"))
    }

    @Test fun extensoesDaBusca() {
        assertEquals(setOf("pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "txt"), TipoDocumento.EXTENSOES.toSet())
    }

    @Test fun textoUtf8ComBomIdaEVolta() {
        val bytes = "﻿Olá, mundo".toByteArray(Charsets.UTF_8)
        val lido = Codificacao.decodificar(bytes)
        assertEquals("Olá, mundo", lido.texto)
        assertEquals(true, lido.comBom)
        assertArrayEquals(bytes, Codificacao.codificar(lido.texto, lido.charset, lido.comBom))
    }

    @Test fun textoWindows1252() {
        val bytes = "Ação".toByteArray(charset("windows-1252"))
        val lido = Codificacao.decodificar(bytes)
        assertEquals("Ação", lido.texto)
        assertEquals("windows-1252", lido.charset.name())
    }
}
