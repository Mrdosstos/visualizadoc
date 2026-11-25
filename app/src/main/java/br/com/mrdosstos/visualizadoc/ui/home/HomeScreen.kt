@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package br.com.mrdosstos.visualizadoc.ui.home

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.TipoDocumento
import br.com.mrdosstos.visualizadoc.data.CriterioOrdem
import br.com.mrdosstos.visualizadoc.data.ItemArquivo
import br.com.mrdosstos.visualizadoc.data.Ordenacao
import br.com.mrdosstos.visualizadoc.data.Ordenador
import br.com.mrdosstos.visualizadoc.data.Tema
import br.com.mrdosstos.visualizadoc.pdf.PdfEditavel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Secao { INICIO, RECENTES, FAVORITOS }

/** Aba de tipo de documento, com a cor do cabeçalho quando selecionada. */
private data class Aba(val tipo: TipoDocumento?, val nome: String, val cor: Color)

private val ABAS = listOf(
    Aba(null, "Todos", Color(0xFF3949AB)),
    Aba(TipoDocumento.WORD, "Word", Color(0xFF2B7CF6)),
    Aba(TipoDocumento.PDF, "PDF", Color(0xFFEF4136)),
    Aba(TipoDocumento.PLANILHA, "Excel", Color(0xFF1FA855)),
    Aba(TipoDocumento.APRESENTACAO, "PPT", Color(0xFFF2731D)),
    Aba(TipoDocumento.TEXTO, "TXT", Color(0xFF5463A8)),
)

/** Tudo o que a tela inicial mostra. */
data class DadosInicio(
    val arquivosDoAparelho: List<ItemArquivo>,
    val temPermissao: Boolean,
    val carregando: Boolean,
    val recentes: List<ItemArquivo>,
    val favoritos: List<ItemArquivo>,
    val ordenacao: Ordenacao,
    val tema: Tema,
    val versao: String,
)

/** Ações que a tela inicial dispara. */
class AcoesInicio(
    val abrir: (ItemArquivo) -> Unit,
    val abrirSeletor: (TipoDocumento?) -> Unit,
    val novo: (TipoDocumento) -> Unit,
    val pedirPermissao: () -> Unit,
    val alternarFavorito: (ItemArquivo) -> Unit,
    val excluir: (List<ItemArquivo>) -> Unit,
    val compartilhar: (List<ItemArquivo>) -> Unit,
    val removerRecente: (ItemArquivo) -> Unit,
    val definirOrdenacao: (Ordenacao) -> Unit,
    val definirTema: (Tema) -> Unit,
    val compartilharApp: () -> Unit,
)

@Composable
fun HomeScreen(dados: DadosInicio, acoes: AcoesInicio) {
    var secao by rememberSaveable { mutableStateOf(Secao.INICIO) }
    var indiceAba by rememberSaveable { mutableIntStateOf(0) }
    var busca by rememberSaveable { mutableStateOf("") }
    var selecionados by remember { mutableStateOf(emptySet<String>()) }
    var modoSelecao by remember { mutableStateOf(false) }
    var mostrarOrdenacao by remember { mutableStateOf(false) }
    var confirmarExclusao by remember { mutableStateOf<List<ItemArquivo>?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val escopo = rememberCoroutineScope()

    val aba = ABAS[indiceAba]
    val corCabecalho by animateColorAsState(aba.cor, label = "corCabecalho")
    BarraDeStatus(if (modoSelecao) MaterialTheme.colorScheme.surface else corCabecalho)

    val favoritosUris = dados.favoritos.map { it.uri }.toSet()
    val base = when (secao) {
        Secao.INICIO -> if (dados.temPermissao) dados.arquivosDoAparelho else dados.recentes
        Secao.RECENTES -> dados.recentes
        Secao.FAVORITOS -> dados.favoritos
    }
    val termo = PdfEditavel.normalizar(busca.trim())
    val filtrados = base.filter {
        (aba.tipo == null || it.tipo == aba.tipo) && (termo.isEmpty() || PdfEditavel.normalizar(it.nome).contains(termo))
    }
    // Recentes seguem a ordem de abertura; as outras listas, a ordenação escolhida.
    val lista = if (secao == Secao.RECENTES) filtrados else Ordenador.ordenar(filtrados, dados.ordenacao)
    val itensSelecionados = lista.filter { it.uri in selecionados }

    fun sairDaSelecao() {
        modoSelecao = false
        selecionados = emptySet()
    }
    BackHandler(enabled = modoSelecao) { sairDaSelecao() }
    BackHandler(enabled = !modoSelecao && drawer.isOpen) { escopo.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            MenuLateral(
                tema = dados.tema,
                versao = dados.versao,
                onTema = acoes.definirTema,
                onCompartilharApp = {
                    escopo.launch { drawer.close() }
                    acoes.compartilharApp()
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                if (modoSelecao) {
                    BarraSelecao(
                        quantidade = itensSelecionados.size,
                        todos = lista.isNotEmpty() && itensSelecionados.size == lista.size,
                        onVoltar = { sairDaSelecao() },
                        onTodos = { marcar -> selecionados = if (marcar) lista.map { it.uri }.toSet() else emptySet() },
                    )
                } else {
                    Cabecalho(
                        cor = corCabecalho,
                        busca = busca,
                        onBusca = { busca = it },
                        indiceAba = indiceAba,
                        onAba = { indiceAba = it },
                        onMenu = { escopo.launch { drawer.open() } },
                        onOrdenar = { mostrarOrdenacao = true },
                        onSelecionar = { modoSelecao = true },
                    )
                }
            },
            bottomBar = {
                if (modoSelecao) {
                    BarraAcoesSelecao(
                        habilitado = itensSelecionados.isNotEmpty(),
                        onExcluir = { confirmarExclusao = itensSelecionados },
                        onCompartilhar = { acoes.compartilhar(itensSelecionados) },
                    )
                } else {
                    NavigationBar {
                        NavigationBarItem(
                            selected = secao == Secao.INICIO,
                            onClick = { secao = Secao.INICIO },
                            icon = { Icon(painterResource(R.drawable.ic_inicio), null) },
                            label = { Text("Início") },
                        )
                        NavigationBarItem(
                            selected = secao == Secao.RECENTES,
                            onClick = { secao = Secao.RECENTES },
                            icon = { Icon(painterResource(R.drawable.ic_recentes), null) },
                            label = { Text("Recentes") },
                        )
                        NavigationBarItem(
                            selected = secao == Secao.FAVORITOS,
                            onClick = { secao = Secao.FAVORITOS },
                            icon = { Icon(painterResource(R.drawable.ic_favorito_borda), null) },
                            label = { Text("Favoritos") },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!modoSelecao) BotaoNovo(cor = corCabecalho, acoes = acoes, tipoAba = aba.tipo)
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                if (secao == Secao.INICIO && !dados.temPermissao) {
                    item(key = "aviso") { AvisoPermissao(acoes.pedirPermissao) }
                }
                if (secao == Secao.INICIO && dados.carregando && lista.isEmpty()) {
                    item(key = "carregando") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (lista.isEmpty() && !dados.carregando) {
                    item(key = "vazia") { ListaVazia(secao, aba, busca.isNotBlank()) }
                }
                items(lista, key = { it.uri }) { item ->
                    LinhaArquivo(
                        item = item,
                        favorito = item.uri in favoritosUris,
                        modoSelecao = modoSelecao,
                        selecionado = item.uri in selecionados,
                        mostrarRemoverRecente = secao == Secao.RECENTES,
                        onClick = {
                            if (modoSelecao) {
                                selecionados = if (item.uri in selecionados) selecionados - item.uri else selecionados + item.uri
                            } else {
                                acoes.abrir(item)
                            }
                        },
                        onLongClick = {
                            modoSelecao = true
                            selecionados = selecionados + item.uri
                        },
                        onFavorito = { acoes.alternarFavorito(item) },
                        onCompartilhar = { acoes.compartilhar(listOf(item)) },
                        onExcluir = { confirmarExclusao = listOf(item) },
                        onRemoverRecente = { acoes.removerRecente(item) },
                    )
                }
            }
        }
    }

    if (mostrarOrdenacao) {
        FolhaOrdenacao(
            atual = dados.ordenacao,
            onAplicar = {
                acoes.definirOrdenacao(it)
                mostrarOrdenacao = false
            },
            onFechar = { mostrarOrdenacao = false },
        )
    }

    confirmarExclusao?.let { itens ->
        AlertDialog(
            onDismissRequest = { confirmarExclusao = null },
            title = { Text(if (itens.size == 1) "Excluir arquivo?" else "Excluir ${itens.size} arquivos?") },
            text = {
                Text(
                    (if (itens.size == 1) "\"${itens[0].nome}\" será apagado do aparelho."
                    else "Os arquivos serão apagados do aparelho.") + " Não dá para desfazer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    acoes.excluir(itens)
                    confirmarExclusao = null
                    sairDaSelecao()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = null }) { Text("Cancelar") } },
        )
    }
}

/** Ícones da barra de status claros sobre fundo escuro (e vice-versa) enquanto a tela inicial estiver aberta. */
@Composable
private fun BarraDeStatus(fundo: Color) {
    val view = LocalView.current
    val iconesEscuros = fundo.luminance() >= 0.5f
    DisposableEffect(iconesEscuros) {
        val janela = (view.context as? Activity)?.window
        val controle = janela?.let { WindowCompat.getInsetsController(it, view) }
        val anterior = controle?.isAppearanceLightStatusBars
        controle?.isAppearanceLightStatusBars = iconesEscuros
        onDispose { if (controle != null && anterior != null) controle.isAppearanceLightStatusBars = anterior }
    }
}

@Composable
private fun Cabecalho(
    cor: Color,
    busca: String,
    onBusca: (String) -> Unit,
    indiceAba: Int,
    onAba: (Int) -> Unit,
    onMenu: () -> Unit,
    onOrdenar: () -> Unit,
    onSelecionar: () -> Unit,
) {
    Surface(color = cor, contentColor = Color.White) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_menu), contentDescription = "Menu")
                }
                // Campo de busca em "pílula" translúcida.
                Row(
                    Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp))
                        .background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_buscar), contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (busca.isEmpty()) {
                            Text("Pesquisar", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = busca,
                            onValueChange = onBusca,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (busca.isNotEmpty()) {
                        IconButton(onClick = { onBusca("") }, modifier = Modifier.size(32.dp)) {
                            Icon(painterResource(R.drawable.ic_fechar), contentDescription = "Limpar busca")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = indiceAba,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 4.dp,
                    divider = {},
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(indiceAba, matchContentSize = true),
                            width = 24.dp,
                            color = Color.White,
                        )
                    },
                ) {
                    ABAS.forEachIndexed { i, a ->
                        Tab(
                            selected = i == indiceAba,
                            onClick = { onAba(i) },
                            selectedContentColor = Color.White,
                            unselectedContentColor = Color.White.copy(alpha = 0.65f),
                            text = {
                                Text(a.nome, fontSize = 17.sp, fontWeight = if (i == indiceAba) FontWeight.Bold else FontWeight.Normal)
                            },
                        )
                    }
                }
                IconButton(onClick = onOrdenar) {
                    Icon(painterResource(R.drawable.ic_ordenar), contentDescription = "Ordenar")
                }
                IconButton(onClick = onSelecionar) {
                    Icon(painterResource(R.drawable.ic_selecionar), contentDescription = "Selecionar")
                }
            }
        }
    }
}

@Composable
private fun BarraSelecao(quantidade: Int, todos: Boolean, onVoltar: () -> Unit, onTodos: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onVoltar) {
                Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Sair da seleção")
            }
            Text("Selecionados ($quantidade)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Todos", style = MaterialTheme.typography.titleMedium)
            Checkbox(checked = todos, onCheckedChange = onTodos)
        }
    }
}

@Composable
private fun BarraAcoesSelecao(habilitado: Boolean, onExcluir: () -> Unit, onCompartilhar: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp)) {
            TextButton(onClick = onExcluir, enabled = habilitado, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_excluir), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Excluir", fontSize = 17.sp)
            }
            TextButton(onClick = onCompartilhar, enabled = habilitado, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_compartilhar), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartilhar", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun BotaoNovo(cor: Color, acoes: AcoesInicio, tipoAba: TipoDocumento?) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(
            onClick = { aberto = true },
            shape = CircleShape,
            containerColor = cor,
            contentColor = Color.White,
        ) {
            Icon(painterResource(R.drawable.ic_novo), contentDescription = "Novo ou abrir arquivo")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = { Text("Novo documento Word") },
                leadingIcon = { TipoIcone("docx", 28.dp) },
                onClick = {
                    aberto = false
                    acoes.novo(TipoDocumento.WORD)
                },
            )
            DropdownMenuItem(
                text = { Text("Nova planilha") },
                leadingIcon = { TipoIcone("xlsx", 28.dp) },
                onClick = {
                    aberto = false
                    acoes.novo(TipoDocumento.PLANILHA)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Abrir outro arquivo…") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_abrir), null) },
                onClick = {
                    aberto = false
                    acoes.abrirSeletor(tipoAba)
                },
            )
        }
    }
}

@Composable
private fun AvisoPermissao(onPermitir: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ver todos os documentos do celular", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Para listar seus PDF, Word, Excel e TXT, permita o acesso aos arquivos. " +
                    "Sem isso, aparecem só os arquivos que você abrir pelo app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPermitir) { Text("Permitir acesso") }
        }
    }
}

@Composable
private fun ListaVazia(secao: Secao, aba: Aba, buscando: Boolean) {
    val texto = when {
        buscando -> "Nenhum arquivo encontrado."
        secao == Secao.FAVORITOS -> "Nenhum favorito ainda.\nToque na estrela de um arquivo para guardá-lo aqui."
        secao == Secao.RECENTES -> "Nenhum arquivo aberto recentemente."
        aba.tipo == null -> "Nenhum documento encontrado."
        else -> "Nenhum arquivo ${aba.nome} encontrado."
    }
    Text(
        texto,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    )
}

@Composable
private fun LinhaArquivo(
    item: ItemArquivo,
    favorito: Boolean,
    modoSelecao: Boolean,
    selecionado: Boolean,
    mostrarRemoverRecente: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorito: () -> Unit,
    onCompartilhar: () -> Unit,
    onExcluir: () -> Unit,
    onRemoverRecente: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val fundo = if (selecionado) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(fundo)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TipoIcone(item.nome, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(item.nome, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            val detalhe = buildString {
                append(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.data)))
                item.tamanho?.let { append("   ").append(Formatter.formatShortFileSize(context, it)) }
            }
            Text(detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (modoSelecao) {
            Checkbox(checked = selecionado, onCheckedChange = { onClick() })
        } else {
            IconButton(onClick = onFavorito) {
                Icon(
                    painterResource(if (favorito) R.drawable.ic_favorito else R.drawable.ic_favorito_borda),
                    contentDescription = if (favorito) "Remover dos favoritos" else "Favoritar",
                    tint = if (favorito) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_mais), contentDescription = "Mais opções")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Compartilhar") }, onClick = { menu = false; onCompartilhar() })
                    DropdownMenuItem(
                        text = { Text(if (favorito) "Remover dos favoritos" else "Favoritar") },
                        onClick = { menu = false; onFavorito() },
                    )
                    if (mostrarRemoverRecente) {
                        DropdownMenuItem(text = { Text("Remover dos recentes") }, onClick = { menu = false; onRemoverRecente() })
                    }
                    DropdownMenuItem(
                        text = { Text("Excluir do aparelho", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onExcluir() },
                    )
                }
            }
        }
    }
}

/** Quadrado colorido com a letra do tipo (W, PDF, X, CSV, P, T), escolhido pela extensão. */
@Composable
private fun TipoIcone(nome: String, tamanho: Dp) {
    val (rotulo, cor) = when (nome.substringAfterLast('.', nome).lowercase()) {
        "docx", "doc" -> "W" to Color(0xFF2B7CF6)
        "pdf" -> "PDF" to Color(0xFFEF4136)
        "xlsx", "xls" -> "X" to Color(0xFF1FA855)
        "csv" -> "CSV" to Color(0xFF1FA855)
        "pptx", "ppt" -> "P" to Color(0xFFF2731D)
        "txt" -> "T" to Color(0xFF5463A8)
        else -> "?" to Color(0xFF757575)
    }
    Box(
        Modifier.size(width = tamanho * 0.85f, height = tamanho).clip(RoundedCornerShape(tamanho * 0.14f)).background(cor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rotulo,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (tamanho.value * if (rotulo.length > 1) 0.26f else 0.42f).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun FolhaOrdenacao(atual: Ordenacao, onAplicar: (Ordenacao) -> Unit, onFechar: () -> Unit) {
    var criterio by remember { mutableStateOf(atual.criterio) }
    var crescente by remember { mutableStateOf(atual.crescente) }
    ModalBottomSheet(onDismissRequest = onFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Ordenar por", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    listOf(CriterioOrdem.DATA to "Data", CriterioOrdem.NOME to "Nome", CriterioOrdem.TAMANHO to "Tamanho do arquivo")
                        .forEach { (c, nome) -> OpcaoRadio(nome, criterio == c) { criterio = c } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    OpcaoRadio("Crescente", crescente) { crescente = true }
                    OpcaoRadio("Decrescente", !crescente) { crescente = false }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAplicar(Ordenacao(criterio, crescente)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("APLICAR", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OpcaoRadio(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal,
        )
        RadioButton(selected = selecionado, onClick = onClick)
    }
}

@Composable
private fun MenuLateral(tema: Tema, versao: String, onTema: (Tema) -> Unit, onCompartilharApp: () -> Unit) {
    var temaAberto by remember { mutableStateOf(false) }
    ModalDrawerSheet {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                TipoIcone("docx", 52.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("VisualizaDoc", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Grátis e sem anúncios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NavigationDrawerItem(
                label = {
                    Column {
                        Text("Tema do app")
                        Text(
                            when (tema) {
                                Tema.SISTEMA -> "Igual ao sistema"
                                Tema.CLARO -> "Claro"
                                Tema.ESCURO -> "Escuro"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                icon = { Icon(painterResource(R.drawable.ic_tema), null) },
                selected = false,
                onClick = { temaAberto = !temaAberto },
            )
            if (temaAberto) {
                Column(Modifier.padding(start = 40.dp)) {
                    listOf(Tema.SISTEMA to "Igual ao sistema", Tema.CLARO to "Claro", Tema.ESCURO to "Escuro").forEach { (t, nome) ->
                        OpcaoRadio(nome, tema == t) { onTema(t) }
                    }
                }
            }
            NavigationDrawerItem(
                label = { Text("Compartilhar o app") },
                icon = { Icon(painterResource(R.drawable.ic_compartilhar), null) },
                selected = false,
                onClick = onCompartilharApp,
            )
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Text(
                "Versão $versao",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            )
        }
    }
}
