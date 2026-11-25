package br.com.mrdosstos.visualizadoc.ui.texto

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.mrdosstos.visualizadoc.R
import br.com.mrdosstos.visualizadoc.core.ArquivoUtils
import br.com.mrdosstos.visualizadoc.core.Compartilhar
import br.com.mrdosstos.visualizadoc.core.ResultadoSalvar
import kotlinx.coroutines.launch

private const val MIME_TXT = "text/plain"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextoScreen(editor: EditorTexto, onVoltar: () -> Unit) {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuAberto by remember { mutableStateOf(false) }
    var confirmarSaida by remember { mutableStateOf(false) }
    val avisar: (String?) -> Unit = { msg -> if (msg != null) escopo.launch { snackbar.showSnackbar(msg) } }

    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_TXT)) { uri ->
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
    val pronto = editor.estado == EstadoTexto.Pronto

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
                    IconButton(
                        onClick = { avisar(editor.alternarEdicao()) },
                        enabled = pronto,
                        colors = if (editor.editando) IconButtonDefaults.filledTonalIconButtonColors()
                        else IconButtonDefaults.iconButtonColors(),
                    ) {
                        Icon(painterResource(R.drawable.ic_editar), contentDescription = "Editar")
                    }
                    if (editor.modificado) {
                        IconButton(onClick = { salvar(false) }, enabled = !editor.salvando) {
                            Icon(painterResource(R.drawable.ic_salvar), contentDescription = "Salvar")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuAberto = true }, enabled = pronto) {
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
                                    else Compartilhar.enviar(context, editor.documento.uri, MIME_TXT)
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
                EstadoTexto.Carregando -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is EstadoTexto.Erro -> Text(
                    estado.mensagem,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                EstadoTexto.Pronto -> if (editor.editando) {
                    OutlinedTextField(
                        value = editor.texto,
                        onValueChange = { editor.aoDigitar(it) },
                        modifier = Modifier.fillMaxSize().padding(8.dp).imePadding(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (editor.somenteLeitura) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "Arquivo grande: aberto só para leitura.",
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                itemsIndexed(editor.linhas) { i, linha ->
                                    Row {
                                        Text(
                                            "${i + 1}",
                                            modifier = Modifier.padding(end = 10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Text(
                                            linha,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                    }
                }
            }
            if (editor.salvando) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = {}),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Salvar alterações?") },
            text = { Text("O texto tem alterações que ainda não foram salvas.") },
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
