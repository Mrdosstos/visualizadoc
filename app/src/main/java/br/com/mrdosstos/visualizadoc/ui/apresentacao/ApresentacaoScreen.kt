package br.com.mrdosstos.visualizadoc.ui.apresentacao

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.apresentacao.ElementoSlide
import br.com.mrdosstos.visualizadoc.apresentacao.LeitorApresentacao
import br.com.mrdosstos.visualizadoc.apresentacao.Slide
import br.com.mrdosstos.visualizadoc.core.Compartilhar
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private val LARANJA = Color(0xFFF2731D)

sealed interface EstadoApresentacao {
    data object Carregando : EstadoApresentacao
    data class Erro(val mensagem: String) : EstadoApresentacao
    data class Pronta(val slides: List<Slide>) : EstadoApresentacao
}

/** Carrega os slides em segundo plano; a tela só lê o estado. */
class EditorApresentacao(
    val documento: DocumentoAberto,
    private val app: Application,
    escopo: CoroutineScope,
) {
    var estado by mutableStateOf<EstadoApresentacao>(EstadoApresentacao.Carregando)
        private set

    init {
        escopo.launch {
            estado = try {
                val slides = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(documento.uri)?.use { LeitorApresentacao.ler(it, documento.nome) }
                        ?: throw FileNotFoundException("Não foi possível ler o arquivo")
                }
                EstadoApresentacao.Pronta(slides)
            } catch (_: OutOfMemoryError) {
                EstadoApresentacao.Erro("A apresentação é grande demais para abrir neste aparelho.")
            } catch (e: Throwable) {
                EstadoApresentacao.Erro("Não foi possível ler a apresentação.\n${e.message ?: ""}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApresentacaoScreen(editor: EditorApresentacao, onVoltar: () -> Unit, onAbrirEmOutroApp: () -> Unit) {
    val context = LocalContext.current
    var menuAberto by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val estado = editor.estado
    val total = (estado as? EstadoApresentacao.Pronta)?.slides?.size ?: 0
    // O primeiro item da lista é o aviso; os slides começam no índice 1.
    val slideAtual by remember { derivedStateOf { listState.firstVisibleItemIndex.coerceAtLeast(1) } }
    BackHandler(onBack = onVoltar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(editor.documento.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (total > 0) {
                            Text("Slide ${slideAtual.coerceAtMost(total)} de $total", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuAberto = true }) {
                            Icon(painterResource(R.drawable.ic_mais), contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                            DropdownMenuItem(
                                text = { Text("Abrir em outro app") },
                                onClick = { menuAberto = false; onAbrirEmOutroApp() },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartilhar") },
                                onClick = {
                                    menuAberto = false
                                    Compartilhar.enviar(
                                        context, editor.documento.uri, TipoDocumento.mimeDoNome(editor.documento.nome),
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainer)) {
            when (estado) {
                EstadoApresentacao.Carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is EstadoApresentacao.Erro -> Text(
                    estado.mensagem,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is EstadoApresentacao.Pronta -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "aviso") {
                        Text(
                            "Visualização simplificada: textos, tabelas e imagens de cada slide. " +
                                "Para ver o layout original, use \"Abrir em outro app\" no menu ⋮.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (estado.slides.isEmpty()) {
                        item(key = "vazio") { Text("A apresentação não tem slides.") }
                    }
                    items(estado.slides, key = { it.numero }) { slide -> CartaoSlide(slide) }
                }
            }
        }
    }
}

@Composable
private fun CartaoSlide(slide: Slide) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.heightIn(min = 160.dp)) {
            Surface(color = LARANJA, contentColor = Color.White) {
                Text(
                    "Slide ${slide.numero}",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slide.titulo?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                slide.elementos.forEach { elemento ->
                    when (elemento) {
                        is ElementoSlide.Texto -> Row(Modifier.padding(start = (elemento.nivel * 16).dp)) {
                            if (elemento.topico) Text("•  ", style = MaterialTheme.typography.bodyLarge)
                            Text(elemento.texto, style = MaterialTheme.typography.bodyLarge)
                        }
                        is ElementoSlide.Tabela -> TabelaSlide(elemento.linhas)
                        is ElementoSlide.Imagem -> ImagemSlide(elemento.dados)
                    }
                }
                if (slide.titulo == null && slide.elementos.isEmpty()) {
                    Text(
                        "(slide sem texto)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                slide.notas?.let {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Notas", style = MaterialTheme.typography.labelLarge, color = LARANJA)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TabelaSlide(linhas: List<List<String>>) {
    val cores = MaterialTheme.colorScheme
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        linhas.forEachIndexed { l, linha ->
            Row {
                linha.forEach { texto ->
                    Box(
                        Modifier.width(120.dp).heightIn(min = 36.dp)
                            .background(if (l == 0) cores.surfaceVariant else cores.surface)
                            .border(0.5.dp, cores.outline)
                            .padding(6.dp),
                    ) {
                        Text(
                            texto,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (l == 0) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagemSlide(dados: ByteArray) {
    val bitmap by produceState<Bitmap?>(initialValue = null, dados) {
        value = withContext(Dispatchers.Default) { decodificar(dados) }
    }
    val atual = bitmap
    if (atual != null) {
        Image(
            bitmap = atual.asImageBitmap(),
            contentDescription = "Imagem do slide",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(atual.width.toFloat() / atual.height),
        )
    } else {
        Spacer(Modifier.heightIn(min = 0.dp))
    }
}

/** Decodifica reduzindo imagens grandes (até ~1600 px) para poupar memória. */
private fun decodificar(dados: ByteArray): Bitmap? = runCatching {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(dados, 0, dados.size, limites)
    if (limites.outWidth <= 0) return@runCatching null
    var amostra = 1
    while (limites.outWidth / amostra > 1600) amostra *= 2
    BitmapFactory.decodeByteArray(dados, 0, dados.size, BitmapFactory.Options().apply { inSampleSize = amostra })
}.getOrNull()
