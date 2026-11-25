package br.com.mrdosstos.visualizadoc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.TipoDocumento

/** Tela provisória para os formatos que chegam nas próximas fases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmBreveScreen(documento: DocumentoAberto, onVoltar: () -> Unit) {
    val mensagem = when (documento.tipo) {
        TipoDocumento.PLANILHA -> "A visualização e edição de planilhas chega na próxima versão."
        TipoDocumento.WORD -> "A visualização de documentos Word chega em breve."
        else -> "Este tipo de arquivo não é suportado."
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(documento.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(mensagem, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
