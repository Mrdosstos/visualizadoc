package br.com.mrdosstos.visualizadoc.ui.documento

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import br.com.mrdosstos.visualizadoc.documento.Alinhamento
import br.com.mrdosstos.visualizadoc.documento.Bloco
import br.com.mrdosstos.visualizadoc.documento.Formato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Célula de tabela em edição: tabela, linha, coluna e o texto digitado. */
private data class CelulaEmEdicao(val tabela: Int, val linha: Int, val coluna: Int, val texto: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentoScreen(editor: EditorDocumento, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmarSaida by remember { mutableStateOf(false) }
    var menuAberto by remember { mutableStateOf(false) }
    var celula by remember { mutableStateOf<CelulaEmEdicao?>(null) }

    val avisar: (String?) -> Unit = { erro -> if (erro != null) escopo.launch { snackbar.showSnackbar(erro) } }

    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_DOCX)) { uri ->
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
        if (editor.editando != null) {
            avisar(editor.concluirEdicao())
        } else if (editor.modificado) {
            confirmarSaida = true
        } else {
            onVoltar()
        }
    }
    BackHandler(onBack = tentarVoltar)

    val pronto = editor.estado == EstadoDocumento.Pronto

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
                    IconButton(onClick = { salvar(false) }, enabled = pronto && !editor.salvando) {
                        Icon(painterResource(R.drawable.ic_salvar), contentDescription = "Salvar")
                    }
                    Box {
                        IconButton(onClick = { menuAberto = true }, enabled = pronto) {
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
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (editor.editando != null) BarraDeFormatacao(editor, avisar)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val estado = editor.estado) {
                EstadoDocumento.Carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is EstadoDocumento.Erro -> Text(
                    estado.mensagem,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                EstadoDocumento.Pronto -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(editor.blocos, key = { it.chave }) { bloco ->
                        when (bloco) {
                            is Bloco.Paragrafo ->
                                if (editor.editando == bloco.indice) {
                                    OutlinedTextField(
                                        value = editor.textoEdicao,
                                        onValueChange = { editor.textoEdicao = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = estiloDoParagrafo(bloco),
                                        minLines = 2,
                                    )
                                } else {
                                    Paragrafo(bloco) { avisar(editor.editar(bloco)) }
                                }
                            is Bloco.Tabela -> Tabela(bloco) { linha, coluna ->
                                avisar(editor.concluirEdicao())
                                celula = CelulaEmEdicao(bloco.indice, linha, coluna, bloco.linhas[linha][coluna])
                            }
                            is Bloco.Imagem -> Imagem(bloco)
                        }
                    }
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

    celula?.let { atual ->
        AlertDialog(
            onDismissRequest = { celula = null },
            title = { Text("Editar célula") },
            text = {
                OutlinedTextField(
                    value = atual.texto,
                    onValueChange = { celula = atual.copy(texto = it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    avisar(editor.editarCelula(atual.tabela, atual.linha, atual.coluna, atual.texto))
                    celula = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { celula = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Salvar alterações?") },
            text = { Text("O documento tem alterações que ainda não foram salvas.") },
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
private fun BarraDeFormatacao(editor: EditorDocumento, avisar: (String?) -> Unit) {
    var menuEstilo by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = editor.formatoAtivo(Formato.NEGRITO),
                onClick = { avisar(editor.alternarFormato(Formato.NEGRITO)) },
                label = { Text("N", fontWeight = FontWeight.Bold) },
            )
            FilterChip(
                selected = editor.formatoAtivo(Formato.ITALICO),
                onClick = { avisar(editor.alternarFormato(Formato.ITALICO)) },
                label = { Text("I", fontStyle = FontStyle.Italic) },
            )
            FilterChip(
                selected = editor.formatoAtivo(Formato.SUBLINHADO),
                onClick = { avisar(editor.alternarFormato(Formato.SUBLINHADO)) },
                label = { Text("S", textDecoration = TextDecoration.Underline) },
            )
            Box {
                TextButton(onClick = { menuEstilo = true }) { Text("Estilo") }
                DropdownMenu(expanded = menuEstilo, onDismissRequest = { menuEstilo = false }) {
                    listOf("Texto normal" to 0, "Título 1" to 1, "Título 2" to 2, "Título 3" to 3)
                        .forEach { (nome, nivel) ->
                            DropdownMenuItem(
                                text = { Text(nome) },
                                onClick = {
                                    menuEstilo = false
                                    avisar(editor.definirTitulo(nivel))
                                },
                            )
                        }
                }
            }
            TextButton(onClick = { avisar(editor.novoParagrafoAbaixo()) }) { Text("+ Parágrafo") }
            TextButton(onClick = { avisar(editor.excluirParagrafo()) }) { Text("Excluir") }
            TextButton(onClick = { avisar(editor.concluirEdicao()) }) { Text("Concluir") }
        }
    }
}

@Composable
private fun estiloDoParagrafo(p: Bloco.Paragrafo): TextStyle {
    val tipos = MaterialTheme.typography
    val base = when (p.nivelTitulo) {
        -1 -> tipos.headlineMedium
        1 -> tipos.headlineSmall
        2 -> tipos.titleLarge
        3 -> tipos.titleMedium
        4, 5, 6 -> tipos.titleSmall
        else -> tipos.bodyLarge
    }
    val alinhamento = when (p.alinhamento) {
        Alinhamento.CENTRO -> TextAlign.Center
        Alinhamento.DIREITA -> TextAlign.End
        Alinhamento.JUSTIFICADO -> TextAlign.Justify
        Alinhamento.ESQUERDA -> TextAlign.Start
    }
    val peso = if (p.nivelTitulo != 0) FontWeight.SemiBold else null
    return base.copy(textAlign = alinhamento, fontWeight = peso ?: base.fontWeight)
}

@Composable
private fun Paragrafo(p: Bloco.Paragrafo, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
        .clickable(enabled = p.editavel, onClick = onClick)
        .padding(start = (p.nivelLista * 16).dp)
    if (p.trechos.isEmpty() && p.marcador == null) {
        Spacer(modifier.height(16.dp))
        return
    }
    Text(textoAnotado(p), modifier = modifier, style = estiloDoParagrafo(p))
}

private fun textoAnotado(p: Bloco.Paragrafo): AnnotatedString = buildAnnotatedString {
    p.marcador?.let { append("$it ") }
    p.trechos.forEach { t ->
        val decoracoes = buildList {
            if (t.sublinhado) add(TextDecoration.Underline)
            if (t.tachado) add(TextDecoration.LineThrough)
        }
        withStyle(
            SpanStyle(
                fontWeight = if (t.negrito) FontWeight.Bold else null,
                fontStyle = if (t.italico) FontStyle.Italic else null,
                textDecoration = if (decoracoes.isEmpty()) null else TextDecoration.combine(decoracoes),
            )
        ) { append(t.texto) }
    }
}

@Composable
private fun Tabela(tabela: Bloco.Tabela, onCelula: (Int, Int) -> Unit) {
    val cores = MaterialTheme.colorScheme
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        tabela.linhas.forEachIndexed { l, linha ->
            Row {
                linha.forEachIndexed { c, texto ->
                    Box(
                        Modifier.width(140.dp).heightIn(min = 40.dp)
                            .border(0.5.dp, cores.outline)
                            .clickable { onCelula(l, c) }
                            .padding(6.dp),
                    ) {
                        Text(texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Imagem(imagem: Bloco.Imagem) {
    val bitmap by produceState<Bitmap?>(initialValue = null, imagem.chave) {
        value = withContext(Dispatchers.Default) { decodificar(imagem.dados) }
    }
    val atual = bitmap
    if (atual != null) {
        Image(
            bitmap = atual.asImageBitmap(),
            contentDescription = imagem.descricao.ifEmpty { "Imagem" },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(atual.width.toFloat() / atual.height),
        )
    } else {
        Text(
            "[imagem]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decodifica reduzindo imagens grandes (até ~1600 px de largura) para poupar memória. */
private fun decodificar(dados: ByteArray): Bitmap? = runCatching {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(dados, 0, dados.size, limites)
    if (limites.outWidth <= 0) return@runCatching null
    var amostra = 1
    while (limites.outWidth / amostra > 1600) amostra *= 2
    BitmapFactory.decodeByteArray(dados, 0, dados.size, BitmapFactory.Options().apply { inSampleSize = amostra })
}.getOrNull()
