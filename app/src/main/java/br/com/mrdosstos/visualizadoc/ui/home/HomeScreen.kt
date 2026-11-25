@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.com.mrdosstos.visualizadoc.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.ArquivoRecente
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Aparência de cada tipo de documento na tela inicial. */
private data class Categoria(
    val tipo: TipoDocumento,
    val nome: String,
    val secao: String,
    val cor: Color,
    @param:DrawableRes val icone: Int,
)

private val CATEGORIAS = listOf(
    Categoria(TipoDocumento.PDF, "PDF", "PDFs", Color(0xFFE53935), R.drawable.ic_tipo_pdf),
    Categoria(TipoDocumento.PLANILHA, "Planilhas", "Planilhas", Color(0xFF2E7D32), R.drawable.ic_tipo_planilha),
    Categoria(TipoDocumento.WORD, "Word", "Documentos Word", Color(0xFF1E63D6), R.drawable.ic_tipo_word),
)

private val OUTROS = Categoria(TipoDocumento.DESCONHECIDO, "Outros", "Outros", Color(0xFF757575), R.drawable.ic_tipo_word)

private fun categoriaDe(tipo: TipoDocumento) = CATEGORIAS.firstOrNull { it.tipo == tipo } ?: OUTROS

@Composable
fun HomeScreen(
    recentes: List<ArquivoRecente>,
    onAbrirArquivo: (TipoDocumento?) -> Unit,
    onAbrirRecente: (ArquivoRecente) -> Unit,
    onRemoverRecente: (ArquivoRecente) -> Unit,
    onNovo: (TipoDocumento) -> Unit,
) {
    var menuNovo by rememberSaveable { mutableStateOf(false) }
    var filtro by rememberSaveable { mutableStateOf<TipoDocumento?>(null) }
    val cores = MaterialTheme.colorScheme
    val visiveis = recentes.filter { filtro == null || it.tipo == filtro }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to cores.primaryContainer.copy(alpha = 0.45f),
                0.45f to cores.surface,
                1f to cores.surfaceContainerLow,
            )
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item { Cabecalho(recentes.size) }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CATEGORIAS.forEach { cat ->
                        CartaoCategoria(
                            categoria = cat,
                            quantidade = recentes.count { it.tipo == cat.tipo },
                            selecionado = filtro == cat.tipo,
                            onClick = { filtro = if (filtro == cat.tipo) null else cat.tipo },
                            onAbrir = { onAbrirArquivo(cat.tipo) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filtro == null,
                            onClick = { filtro = null },
                            label = { Text("Todos (${recentes.size})") },
                        )
                    }
                    items(CATEGORIAS) { cat ->
                        FilterChip(
                            selected = filtro == cat.tipo,
                            onClick = { filtro = cat.tipo },
                            label = { Text("${cat.nome} (${recentes.count { it.tipo == cat.tipo }})") },
                        )
                    }
                }
            }

            if (visiveis.isEmpty()) {
                item { EstadoVazio(filtro) }
            } else {
                // Em "Todos", a lista é separada em seções por tipo de documento.
                val grupos = if (filtro == null) {
                    visiveis.groupBy { categoriaDe(it.tipo) }
                        .toList()
                        .sortedBy { (cat, _) -> CATEGORIAS.indexOf(cat).let { if (it < 0) 99 else it } }
                } else {
                    listOf(categoriaDe(filtro!!) to visiveis)
                }
                grupos.forEach { (cat, arquivos) ->
                    item(key = "secao-${cat.tipo}") { TituloSecao(cat, arquivos.size) }
                    items(arquivos, key = { it.uri }) { recente ->
                        ItemRecente(
                            recente = recente,
                            categoria = cat,
                            onClick = { onAbrirRecente(recente) },
                            onRemover = { onRemoverRecente(recente) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                SmallFloatingActionButton(onClick = { menuNovo = true }) {
                    Icon(painterResource(R.drawable.ic_novo), contentDescription = "Novo arquivo")
                }
                DropdownMenu(expanded = menuNovo, onDismissRequest = { menuNovo = false }) {
                    DropdownMenuItem(
                        text = { Text("Novo documento Word") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tipo_word), null, tint = CATEGORIAS[2].cor) },
                        onClick = { menuNovo = false; onNovo(TipoDocumento.WORD) },
                    )
                    DropdownMenuItem(
                        text = { Text("Nova planilha") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_tipo_planilha), null, tint = CATEGORIAS[1].cor) },
                        onClick = { menuNovo = false; onNovo(TipoDocumento.PLANILHA) },
                    )
                }
            }
            ExtendedFloatingActionButton(
                onClick = { onAbrirArquivo(filtro) },
                icon = { Icon(painterResource(R.drawable.ic_abrir), contentDescription = null) },
                text = { Text("Abrir arquivo") },
            )
        }
    }
}

@Composable
private fun Cabecalho(total: Int) {
    val cores = MaterialTheme.colorScheme
    val corCirculo = cores.onPrimaryContainer.copy(alpha = 0.06f)
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(cores.primaryContainer, cores.tertiaryContainer)))
            .drawBehind {
                // Círculos decorativos discretos no fundo do cabeçalho.
                drawCircle(corCirculo, radius = size.height * 0.9f, center = Offset(size.width * 0.95f, 0f))
                drawCircle(corCirculo, radius = size.height * 0.45f, center = Offset(size.width * 0.1f, size.height))
            }
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column {
            Text(
                "VisualizaDoc",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = cores.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "PDF, planilhas e Word num só lugar.\nGrátis e sem anúncios.",
                style = MaterialTheme.typography.bodyMedium,
                color = cores.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (total == 1) "1 arquivo recente" else "$total arquivos recentes",
                    style = MaterialTheme.typography.labelLarge,
                    color = cores.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CartaoCategoria(
    categoria: Categoria,
    quantidade: Int,
    selecionado: Boolean,
    onClick: () -> Unit,
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = if (selecionado) 6.dp else 2.dp,
        border = if (selecionado) BorderStroke(2.dp, categoria.cor) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(categoria.cor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(categoria.icone), contentDescription = null, tint = categoria.cor)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    quantidade.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(categoria.nome, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "Abrir",
                style = MaterialTheme.typography.labelLarge,
                color = categoria.cor,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAbrir)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TituloSecao(categoria: Categoria, quantidade: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(categoria.cor))
        Spacer(Modifier.size(8.dp))
        Text(categoria.secao, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            quantidade.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ItemRecente(
    recente: ArquivoRecente,
    categoria: Categoria,
    onClick: () -> Unit,
    onRemover: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(categoria.cor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(categoria.icone), contentDescription = null, tint = categoria.cor)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    recente.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    quando(recente.abertoEm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemover) {
                Icon(
                    painterResource(R.drawable.ic_fechar),
                    contentDescription = "Remover dos recentes",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EstadoVazio(filtro: TipoDocumento?) {
    val texto = when (filtro) {
        null -> "Nenhum arquivo aberto ainda.\nToque em \"Abrir arquivo\" para ver um PDF, uma planilha ou um documento Word."
        else -> "Nenhum arquivo de ${categoriaDe(filtro).nome} aberto ainda.\nToque em \"Abrir\" no cartão acima."
    }
    Text(
        texto,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
    )
}

/** "Hoje, 14:30", "Ontem, 09:10" ou "25/11/2026". */
private fun quando(momento: Long): String {
    val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(momento))
    val hoje = Calendar.getInstance()
    val data = Calendar.getInstance().apply { timeInMillis = momento }
    val mesmoDia = { a: Calendar, b: Calendar ->
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
    val ontem = (hoje.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        mesmoDia(data, hoje) -> "Hoje, $hora"
        mesmoDia(data, ontem) -> "Ontem, $hora"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(momento))
    }
}
