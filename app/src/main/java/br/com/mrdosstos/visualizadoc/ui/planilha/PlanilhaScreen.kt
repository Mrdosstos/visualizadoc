package br.com.mrdosstos.visualizadoc.ui.planilha

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.Compartilhar
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import br.com.mrdosstos.visualizadoc.planilha.Alinhamento
import br.com.mrdosstos.visualizadoc.planilha.CelulaVisual
import kotlinx.coroutines.launch

private val LARGURA_NUMERO_LINHA = 48.dp
private val ALTURA_LINHA = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanilhaScreen(editor: EditorPlanilha, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSaida by remember { mutableStateOf(false) }
    var menuAberto by remember { mutableStateOf(false) }

    val salvarComo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(editor.formato.mime)
    ) { uri ->
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
        if (editor.modificado) {
            confirmarSaida = true
        } else {
            onVoltar()
        }
    }
    BackHandler(onBack = tentarVoltar)

    val pronta = editor.estado as? EstadoPlanilha.Pronta

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (editor.modificado) "• " else "") + editor.documento.nome,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = tentarVoltar) {
                        Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { salvar(false) }, enabled = pronta != null && !editor.salvando) {
                        Icon(painterResource(R.drawable.ic_salvar), contentDescription = "Salvar")
                    }
                    Box {
                        IconButton(onClick = { menuAberto = true }, enabled = pronta != null) {
                            Icon(painterResource(R.drawable.ic_mais), contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                            DropdownMenuItem(
                                text = { Text("Salvar como…") },
                                onClick = {
                                    menuAberto = false
                                    salvarComo.launch(editor.documento.nome)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Compartilhar") },
                                onClick = {
                                    menuAberto = false
                                    if (editor.modificado) {
                                        escopo.launch { snackbar.showSnackbar("Salve as alterações antes de compartilhar.") }
                                    } else {
                                        Compartilhar.enviar(context, editor.documento.uri, editor.formato.mime)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val estado = editor.estado) {
                EstadoPlanilha.Carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is EstadoPlanilha.Erro -> Text(
                    estado.mensagem,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is EstadoPlanilha.Pronta -> ConteudoPlanilha(editor, estado.abas) { erro ->
                    escopo.launch { snackbar.showSnackbar(erro) }
                }
            }
            if (editor.salvando) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                        .clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Salvar alterações?") },
            text = { Text("A planilha tem alterações que ainda não foram salvas.") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConteudoPlanilha(editor: EditorPlanilha, abas: List<String>, onErro: (String) -> Unit) {
    val listState = rememberLazyListState()
    val scrollHorizontal = rememberScrollState()

    // Ao confirmar, a seleção desce uma linha; garante que ela continue visível.
    LaunchedEffect(editor.selecao) {
        val linha = editor.selecao?.first ?: return@LaunchedEffect
        val visiveis = listState.layoutInfo.visibleItemsInfo
        if (visiveis.isNotEmpty() && (linha < visiveis.first().index || linha >= visiveis.last().index)) {
            listState.animateScrollToItem((linha - 2).coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraDeFormula(editor, onErro)
        Box(Modifier.weight(1f)) {
            Grade(editor, listState, scrollHorizontal)
        }
        if (abas.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = editor.abaAtual, edgePadding = 0.dp) {
                abas.forEachIndexed { i, nome ->
                    Tab(
                        selected = i == editor.abaAtual,
                        onClick = { editor.selecionarAba(i) },
                        text = { Text(nome, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BarraDeFormula(editor: EditorPlanilha, onErro: (String) -> Unit) {
    val selecao = editor.selecao
    val confirmar = { editor.confirmarEdicao()?.let(onErro); Unit }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            selecao?.let { (l, c) -> editor.nomeColuna(c) + (l + 1) } ?: "",
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = editor.textoEdicao,
            onValueChange = { editor.textoEdicao = it },
            modifier = Modifier.weight(1f),
            enabled = selecao != null && !editor.salvando,
            singleLine = true,
            placeholder = { Text("Toque numa célula para editar") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirmar() }),
        )
        IconButton(onClick = confirmar, enabled = selecao != null && !editor.salvando) {
            Icon(painterResource(R.drawable.ic_confirmar), contentDescription = "Confirmar")
        }
    }
}

@Composable
private fun Grade(editor: EditorPlanilha, listState: LazyListState, scrollHorizontal: ScrollState) {
    val versao = editor.versao
    val aba = editor.abaAtual
    val (linhas, colunas) = remember(versao, aba) { editor.dimensoesExibicao() }
    val larguras = remember(aba, colunas) { List(colunas) { editor.larguraColunaDp(it).dp } }
    val selecao = editor.selecao
    val corCabecalho = MaterialTheme.colorScheme.surfaceVariant

    Column(Modifier.fillMaxSize()) {
        // Cabeçalho: A, B, C...
        Row(Modifier.height(28.dp).background(corCabecalho)) {
            Box(Modifier.width(LARGURA_NUMERO_LINHA).fillMaxHeight())
            Row(Modifier.horizontalScroll(scrollHorizontal)) {
                for (c in 0 until colunas) {
                    Cabecalho(editor.nomeColuna(c), larguras[c], selecao?.second == c)
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = linhas, key = { it }) { l ->
                Row(Modifier.height(ALTURA_LINHA)) {
                    Cabecalho((l + 1).toString(), LARGURA_NUMERO_LINHA, selecao?.first == l)
                    Row(Modifier.horizontalScroll(scrollHorizontal)) {
                        for (c in 0 until colunas) {
                            val visual = remember(versao, aba, l, c) { editor.celula(l, c) }
                            Celula(
                                visual = visual,
                                largura = larguras[c],
                                selecionada = selecao == (l to c),
                                onClick = { editor.selecionar(l, c) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cabecalho(texto: String, largura: Dp, destacado: Boolean) {
    val cores = MaterialTheme.colorScheme
    Box(
        Modifier.width(largura).fillMaxHeight()
            .background(if (destacado) cores.primaryContainer else cores.surfaceVariant)
            .border(0.5.dp, cores.outlineVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            fontSize = 12.sp,
            color = if (destacado) cores.onPrimaryContainer else cores.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Celula(visual: CelulaVisual, largura: Dp, selecionada: Boolean, onClick: () -> Unit) {
    val cores = MaterialTheme.colorScheme
    val fundo = visual.corFundo?.let { Color(it) }
    Box(
        Modifier.width(largura).fillMaxHeight()
            .background(fundo ?: cores.surface)
            .border(
                if (selecionada) 2.dp else 0.5.dp,
                if (selecionada) cores.primary else cores.outlineVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = when (visual.alinhamento) {
            Alinhamento.ESQUERDA -> Alignment.CenterStart
            Alinhamento.CENTRO -> Alignment.Center
            Alinhamento.DIREITA -> Alignment.CenterEnd
        },
    ) {
        Text(
            visual.texto,
            fontSize = 13.sp,
            fontWeight = if (visual.negrito) FontWeight.Bold else FontWeight.Normal,
            color = if (fundo != null) Color.Black else cores.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
