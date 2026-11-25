package br.com.mrdosstos.visualizadoc.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.ArquivoRecente
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentes: List<ArquivoRecente>,
    onAbrirArquivo: () -> Unit,
    onAbrirRecente: (ArquivoRecente) -> Unit,
    onRemoverRecente: (ArquivoRecente) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("VisualizaDoc") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAbrirArquivo,
                icon = { Icon(painterResource(R.drawable.ic_abrir), contentDescription = null) },
                text = { Text("Abrir arquivo") },
            )
        },
    ) { padding ->
        if (recentes.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nenhum arquivo aberto ainda.\nToque em \"Abrir arquivo\" para ver um PDF, planilha ou documento Word.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                item {
                    Text(
                        "Recentes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(recentes, key = { it.uri }) { recente ->
                    ItemRecente(
                        recente = recente,
                        onClick = { onAbrirRecente(recente) },
                        onRemover = { onRemoverRecente(recente) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRecente(
    recente: ArquivoRecente,
    onClick: () -> Unit,
    onRemover: () -> Unit,
) {
    val data = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(recente.abertoEm))
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { SeloTipo(recente.tipo) },
        headlineContent = {
            Text(recente.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text("${recente.tipo.descricao} · $data") },
        trailingContent = {
            IconButton(onClick = onRemover) {
                Icon(
                    painterResource(R.drawable.ic_fechar),
                    contentDescription = "Remover dos recentes",
                )
            }
        },
    )
}

@Composable
private fun SeloTipo(tipo: TipoDocumento) {
    val cor = when (tipo) {
        TipoDocumento.PDF -> Color(0xFFD32F2F)
        TipoDocumento.PLANILHA -> Color(0xFF2E7D32)
        TipoDocumento.WORD -> Color(0xFF1565C0)
        TipoDocumento.DESCONHECIDO -> Color(0xFF757575)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cor),
        contentAlignment = Alignment.Center,
    ) {
        Text(tipo.sigla, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
