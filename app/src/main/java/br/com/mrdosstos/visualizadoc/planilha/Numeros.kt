package br.com.mrdosstos.visualizadoc.planilha

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Conversão entre texto digitado pelo usuário e números, seguindo o idioma
 * do aparelho (em pt-BR: vírgula decimal, ponto como separador de milhar).
 *
 * É estrita de propósito: "1.5" em pt-BR não vira 15 nem 1,5 — fica como texto,
 * para nunca gravar um número diferente do que a pessoa quis.
 */
object Numeros {

    fun interpretar(texto: String, locale: Locale): Double? {
        var s = texto.trim()
        if (s.isEmpty()) return null

        val simbolos = DecimalFormatSymbols.getInstance(locale)
        val decimal = simbolos.decimalSeparator
        val milhar = simbolos.groupingSeparator

        var fator = 1.0
        if (s.endsWith("%")) {
            fator = 0.01
            s = s.dropLast(1).trimEnd()
        }

        var negativo = false
        if (s.startsWith("-") || s.startsWith("+")) {
            negativo = s[0] == '-'
            s = s.substring(1)
        }
        if (s.isEmpty()) return null

        val partes = s.split(decimal)
        if (partes.size > 2) return null
        val parteInteira = partes[0]
        val parteFracao = partes.getOrNull(1)

        if (parteFracao != null && (parteFracao.isEmpty() || !parteFracao.all { it.isDigit() })) return null
        if (parteInteira.isEmpty() && parteFracao == null) return null

        val inteiro = if (milhar != decimal && parteInteira.contains(milhar)) {
            val grupos = parteInteira.split(milhar)
            val valido = grupos.first().length in 1..3 &&
                grupos.drop(1).all { it.length == 3 } &&
                grupos.all { g -> g.all { it.isDigit() } }
            if (!valido) return null
            grupos.joinToString("")
        } else {
            if (!parteInteira.all { it.isDigit() }) return null
            parteInteira
        }

        val normalizado = buildString {
            append(inteiro.ifEmpty { "0" })
            if (parteFracao != null) append('.').append(parteFracao)
        }
        val valor = normalizado.toDoubleOrNull() ?: return null
        return (if (negativo) -valor else valor) * fator
    }

    /** Número no formato para edição: sem separador de milhar, decimal do idioma. */
    fun formatarParaEdicao(valor: Double, locale: Locale): String {
        if (valor.isNaN() || valor.isInfinite()) return valor.toString()
        val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val texto = BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString()
        return texto.replace('.', decimal)
    }
}
