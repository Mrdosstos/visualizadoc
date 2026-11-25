package br.com.mrdosstos.visualizadoc.ui.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.Compartilhar
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import br.com.mrdosstos.visualizadoc.pdf.TrechoPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val ZOOM_MAXIMO = 4f
private const val LARGURA_MAXIMA_PX = 2048
private const val MIME_PDF = "application/pdf"

/** Trecho escolhido para editar: página, posição na lista e texto digitado. */
private data class TrechoEmEdicao(val pagina: Int, val indice: Int, val texto: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(editor: EditorPdf, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuAberto by remember { mutableStateOf(false) }
    var confirmarSaida by remember { mutableStateOf(false) }
    var trechoEditando by remember { mutableStateOf<TrechoEmEdicao?>(null) }
    val avisar: (String?) -> Unit = { msg -> if (msg != null) escopo.launch { snackbar.showSnackbar(msg) } }

    // Abre (e reabre a cada edição) a cópia que a tela desenha.
    val arquivo = editor.arquivoRender
    val renderizacao by produceState<Result<DocumentoPdf>?>(initialValue = null, arquivo) {
        if (arquivo == null) return@produceState
        val aberto = withContext(Dispatchers.IO) { runCatching { DocumentoPdf.abrirArquivo(arquivo) } }
        value = aberto
        awaitDispose { aberto.getOrNull()?.close() }
    }
    val pdf = renderizacao?.getOrNull()

    val listState = rememberLazyListState()
    val paginaAtual by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    // Ao navegar pelos resultados da busca, leva até a página da ocorrência.
    LaunchedEffect(editor.ocorrenciaAtual) {
        editor.ocorrenciaAtual?.let { listState.animateScrollToItem(it.pagina) }
    }

    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { uri ->
        if (uri != null) escopo.launch {
            ArquivoUtils.manterPermissao(context, uri)
            when (val r = editor.salvar(uri)) {
                ResultadoSalvar.Ok -> snackbar.showSnackbar("Salvo como ${editor.documento.nome}")
                is ResultadoSalvar.Erro -> snackbar.showSnackbar(r.mensagem)
                ResultadoSalvar.PrecisaSalvarComo -> snackbar.showSnackbar("Sem permissão para gravar nesse local.")
            }
        }
    }

    fun salvar(sairDepois: Boolean) {
        escopo.launch {
            when (val r = editor.salvar()) {
                ResultadoSalvar.Ok -> if (sairDepois) onVoltar() else snackbar.showSnackbar("Salvo")
                ResultadoSalvar.PrecisaSalvarComo -> {
                    snackbar.showSnackbar("Este arquivo não pode ser alterado. Escolha onde salvar uma cópia.")
                    salvarComo.launch(editor.documento.nome)
                }
                is ResultadoSalvar.Erro -> snackbar.showSnackbar(r.mensagem)
            }
        }
    }

    val tentarVoltar: () -> Unit = {
        when {
            editor.buscaAberta -> editor.fecharBusca()
            editor.modoEdicao -> escopo.launch { editor.alternarModoEdicao() }
            editor.modificado -> confirmarSaida = true
            else -> onVoltar()
        }
    }
    BackHandler(onBack = tentarVoltar)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                (if (editor.modificado) "• " else "") + editor.documento.nome,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            pdf?.let {
                                Text("Página $paginaAtual de ${it.totalPaginas}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = tentarVoltar) {
                            Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { editor.abrirBusca() }, enabled = pdf != null) {
                            Icon(painterResource(R.drawable.ic_buscar), contentDescription = "Buscar")
                        }
                        IconButton(
                            onClick = { escopo.launch { avisar(editor.alternarModoEdicao()) } },
                            enabled = pdf != null && !editor.preparandoTexto,
                            colors = if (editor.modoEdicao) IconButtonDefaults.filledTonalIconButtonColors()
                            else IconButtonDefaults.iconButtonColors(),
                        ) {
                            Icon(painterResource(R.drawable.ic_editar), contentDescription = "Editar texto")
                        }
                        if (editor.modificado) {
                            IconButton(onClick = { salvar(false) }, enabled = !editor.salvando) {
                                Icon(painterResource(R.drawable.ic_salvar), contentDescription = "Salvar")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuAberto = true }) {
                                Icon(painterResource(R.drawable.ic_mais), contentDescription = "Mais opções")
                            }
                            DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                                DropdownMenuItem(
                                    text = { Text("Salvar como…") },
                                    onClick = { menuAberto = false; salvarComo.launch(editor.documento.nome) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Compartilhar") },
                                    onClick = {
                                        menuAberto = false
                                        if (editor.modificado) avisar("Salve as alterações antes de compartilhar.")
                                        else Compartilhar.enviar(context, editor.documento.uri, MIME_PDF)
                                    },
                                )
                            }
                        }
                    },
                )
                if (editor.buscaAberta) BarraDeBusca(editor, avisar)
                if (editor.modoEdicao) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "Toque num texto marcado para editá-lo.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFF9E9E9E)),
            contentAlignment = Alignment.Center,
        ) {
            val erroRender = renderizacao?.exceptionOrNull()
            when {
                editor.estado is EstadoPdf.Erro -> Mensagem((editor.estado as EstadoPdf.Erro).mensagem)
                erroRender is SecurityException ->
                    Mensagem("Este PDF é protegido por senha. Abrir PDFs com senha ainda não é suportado.")
                erroRender != null -> Mensagem("Não foi possível abrir o PDF.\n${erroRender.message ?: ""}")
                pdf == null -> CircularProgressIndicator()
                else -> PaginasPdf(pdf, editor, listState) { pagina, indice, trecho ->
                    trechoEditando = TrechoEmEdicao(pagina, indice, trecho.texto)
                }
            }
            if (editor.salvando || editor.preparandoTexto) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = {}),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }

    trechoEditando?.let { atual ->
        AlertDialog(
            onDismissRequest = { trechoEditando = null },
            title = { Text("Editar texto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = atual.texto,
                        onValueChange = { trechoEditando = atual.copy(texto = it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "O texto novo é escrito por cima do antigo, no mesmo lugar e tamanho.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    trechoEditando = null
                    escopo.launch { avisar(editor.editar(atual.pagina, atual.indice, atual.texto)) }
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { trechoEditando = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Salvar alterações?") },
            text = { Text("O PDF tem alterações que ainda não foram salvas.") },
            confirmButton = {
                TextButton(onClick = { confirmarSaida = false; salvar(sairDepois = true) }) { Text("Salvar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmarSaida = false; onVoltar() }) { Text("Descartar") }
                    TextButton(onClick = { confirmarSaida = false }) { Text("Cancelar") }
                }
            },
        )
    }
}

@Composable
private fun Mensagem(texto: String) {
    Text(texto, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
}

@Composable
private fun BarraDeBusca(editor: EditorPdf, avisar: (String?) -> Unit) {
    val escopo = rememberCoroutineScope()
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = editor.termoBusca,
                onValueChange = { editor.termoBusca = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar no PDF") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { escopo.launch { avisar(editor.buscar()) } }),
            )
            if (editor.ocorrencias.isNotEmpty()) {
                Text(
                    "${editor.indiceOcorrencia + 1}/${editor.ocorrencias.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            IconButton(onClick = { editor.ocorrenciaAnterior() }, enabled = editor.ocorrencias.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_subir), contentDescription = "Anterior")
            }
            IconButton(onClick = { editor.proximaOcorrencia() }, enabled = editor.ocorrencias.isNotEmpty()) {
                Icon(painterResource(R.drawable.ic_descer), contentDescription = "Próxima")
            }
            IconButton(onClick = { editor.fecharBusca() }) {
                Icon(painterResource(R.drawable.ic_fechar), contentDescription = "Fechar busca")
            }
        }
    }
}

/**
 * Páginas com zoom de verdade: a pinça amplia a partir do ponto entre os dedos e,
 * ao soltar, as páginas são redesenhadas maiores. Ampliado, um dedo arrasta o
 * documento para os lados (rolagem horizontal) e para cima/baixo (lista).
 */
@Composable
private fun PaginasPdf(
    pdf: DocumentoPdf,
    editor: EditorPdf,
    listState: LazyListState,
    onTocarTrecho: (Int, Int, TrechoPdf) -> Unit,
) {
    var escala by remember { mutableFloatStateOf(1f) }        // zoom aplicado ao layout
    var escalaGesto by remember { mutableFloatStateOf(1f) }   // zoom provisório durante a pinça
    var centro by remember { mutableStateOf(Offset.Zero) }
    val scrollHorizontal = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pincou = false
                do {
                    val evento = awaitPointerEvent(PointerEventPass.Initial)
                    if (evento.changes.count { it.pressed } >= 2) {
                        pincou = true
                        centro = evento.calculateCentroid()
                        escalaGesto = (escalaGesto * evento.calculateZoom())
                            .coerceIn(1f / escala, ZOOM_MAXIMO / escala)
                        evento.changes.forEach { it.consume() }
                    }
                } while (evento.changes.any { it.pressed })
                if (pincou) {
                    // Aplica o zoom ao layout e ajusta a rolagem para manter o ponto entre os dedos.
                    val fator = escalaGesto
                    escala = (escala * fator).coerceIn(1f, ZOOM_MAXIMO)
                    escalaGesto = 1f
                    val alvoX = (scrollHorizontal.value + centro.x) * fator - centro.x
                    scrollHorizontal.dispatchRawDelta(alvoX - scrollHorizontal.value)
                    listState.dispatchRawDelta(centro.y * (fator - 1f))
                }
            }
        },
    ) {
        val larguraTela = constraints.maxWidth
        val larguraConteudo = (larguraTela * escala).roundToInt()
        val larguraRender = larguraConteudo.coerceAtMost(LARGURA_MAXIMA_PX)
        val larguraDp = with(density) { larguraConteudo.toDp() }

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = escalaGesto
                    scaleY = escalaGesto
                    transformOrigin = TransformOrigin(
                        if (size.width > 0) centro.x / size.width else 0.5f,
                        if (size.height > 0) centro.y / size.height else 0.5f,
                    )
                }
                .horizontalScroll(scrollHorizontal, enabled = escala > 1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(larguraDp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = pdf.totalPaginas, key = { it }) { indice ->
                    PaginaPdf(pdf, editor, indice, larguraRender, onTocarTrecho)
                }
            }
        }
    }
}

@Composable
private fun PaginaPdf(
    pdf: DocumentoPdf,
    editor: EditorPdf,
    indice: Int,
    larguraPx: Int,
    onTocarTrecho: (Int, Int, TrechoPdf) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pdf, indice, larguraPx) {
        value = withContext(Dispatchers.IO) { runCatching { pdf.renderizar(indice, larguraPx) }.getOrNull() }
    }
    val imagem = bitmap
    val proporcao = if (imagem != null) imagem.width.toFloat() / imagem.height else 1f / pdf.proporcao(indice)

    // Trechos de texto só quando há algo a marcar: modo edição ou resultado de busca nesta página.
    val ocorrenciasAqui = editor.ocorrencias.filter { it.pagina == indice }.map { it.trecho }.toSet()
    val atual = editor.ocorrenciaAtual?.takeIf { it.pagina == indice }?.trecho
    val marcar = editor.modoEdicao || ocorrenciasAqui.isNotEmpty()
    val trechos by produceState(initialValue = emptyList<TrechoPdf>(), marcar, indice, editor.versaoTrechos) {
        value = if (marcar) editor.trechos(indice) else emptyList()
    }

    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(proporcao).background(Color.White)) {
        if (imagem != null) {
            Image(
                bitmap = imagem.asImageBitmap(),
                contentDescription = "Página ${indice + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
        }

        val pontos = pdf.larguraPontos(indice)
        if (marcar && pontos > 0f) {
            val pxPorPonto = constraints.maxWidth / pontos
            val cores = MaterialTheme.colorScheme
            val density = LocalDensity.current
            trechos.forEachIndexed { i, t ->
                val destaque = when {
                    i == atual -> Color(0x99FFC107)
                    i in ocorrenciasAqui -> Color(0x55FFEB3B)
                    editor.modoEdicao -> cores.primary.copy(alpha = 0.10f)
                    else -> null
                } ?: return@forEachIndexed
                val larguraDp = with(density) { (t.largura * pxPorPonto).toDp() }
                val alturaDp = with(density) { (t.altura * pxPorPonto * 1.25f).toDp() }
                Box(
                    Modifier
                        .offset { IntOffset((t.x * pxPorPonto).roundToInt(), (t.topo * pxPorPonto).roundToInt()) }
                        .width(larguraDp)
                        .size(width = larguraDp, height = alturaDp)
                        .background(destaque)
                        .then(
                            if (editor.modoEdicao) {
                                Modifier.border(BorderStroke(1.dp, cores.primary.copy(alpha = 0.5f)))
                                    .clickable { onTocarTrecho(indice, i, t) }
                            } else Modifier
                        ),
                )
            }
        }
    }
}
