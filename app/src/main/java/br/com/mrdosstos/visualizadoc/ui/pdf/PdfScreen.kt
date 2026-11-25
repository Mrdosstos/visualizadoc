package br.com.mrdosstos.visualizadoc.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.DocumentoAberto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ZOOM_MAXIMO = 4f
private const val LARGURA_MAXIMA_PX = 2048

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(documento: DocumentoAberto, onVoltar: () -> Unit) {
    val context = LocalContext.current
    var pdf by remember { mutableStateOf<DocumentoPdf?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(documento.uri) {
        try {
            pdf = withContext(Dispatchers.IO) { DocumentoPdf.abrir(context, documento.uri) }
        } catch (_: SecurityException) {
            erro = "Este PDF é protegido por senha. Abrir PDFs com senha ainda não é suportado."
        } catch (e: Exception) {
            erro = "Não foi possível abrir o PDF.\n${e.message ?: ""}"
        }
    }
    DisposableEffect(pdf) {
        val atual = pdf
        onDispose { atual?.close() }
    }

    val listState = rememberLazyListState()
    val paginaAtual by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(documento.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        pdf?.let {
                            Text(
                                "Página $paginaAtual de ${it.totalPaginas}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF9E9E9E)),
            contentAlignment = Alignment.Center,
        ) {
            val atual = pdf
            when {
                erro != null -> Text(
                    erro!!,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
                atual == null -> CircularProgressIndicator()
                else -> PaginasPdf(atual, listState)
            }
        }
    }
}

/** Lista de páginas com pinça para zoom (dois dedos) e rolagem vertical normal. */
@Composable
private fun PaginasPdf(
    pdf: DocumentoPdf,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamentoX by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val evento = awaitPointerEvent(PointerEventPass.Initial)
                        if (evento.changes.count { it.pressed } >= 2) {
                            escala = (escala * evento.calculateZoom()).coerceIn(1f, ZOOM_MAXIMO)
                            val limite = size.width * (escala - 1f) / 2f
                            deslocamentoX = (deslocamentoX + evento.calculatePan().x)
                                .coerceIn(-limite, limite)
                            evento.changes.forEach { it.consume() }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            },
    ) {
        val larguraPx = constraints.maxWidth.coerceAtMost(LARGURA_MAXIMA_PX)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = deslocamentoX
                },
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = pdf.totalPaginas, key = { it }) { indice ->
                PaginaPdf(pdf, indice, larguraPx)
            }
        }
    }
}

@Composable
private fun PaginaPdf(pdf: DocumentoPdf, indice: Int, larguraPx: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pdf, indice, larguraPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { pdf.renderizar(indice, larguraPx) }.getOrNull()
        }
    }
    val imagem = bitmap
    if (imagem != null) {
        Image(
            bitmap = imagem.asImageBitmap(),
            contentDescription = "Página ${indice + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(imagem.width.toFloat() / imagem.height),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / pdf.proporcao(indice))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}
