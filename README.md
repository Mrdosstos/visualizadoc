# VisualizaDoc

App Android gratuito e sem anúncios para visualizar e editar documentos.

| Formato | Situação |
|---|---|
| PDF | ✅ Visualizar (rolagem, zoom com pinça), buscar texto e editar texto (substitui por cima) |
| Planilhas (.xlsx, .xls, .csv) | ✅ Visualizar, editar células (texto, números, fórmulas) e salvar |
| Word (.docx) | ✅ Visualizar (títulos, listas, tabelas, imagens) e editar parágrafos, formatação e células de tabela |
| Word antigo (.doc) | ✅ Ler e converter para .docx |
| Texto (.txt) | ✅ Ler e editar |
| PowerPoint (.pptx, .ppt) | ✅ Visualização simplificada (títulos, textos, tabelas, imagens e notas de cada slide) ou abrir em outro app |
| Geral | ✅ Lista todos os documentos do aparelho por tipo, busca, ordenação, favoritos, seleção múltipla (excluir/compartilhar), tema claro/escuro, criar documento Word ou planilha em branco |

## Download

Baixe o APK mais recente na página de [Releases](https://github.com/Mrdosstos/visualizadoc/releases).

## Compilar

Requisitos: Android Studio recente (com suporte ao AGP 9.2) e JDK 17.

```bash
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/`.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- `PdfRenderer` nativo do Android para exibir PDF e [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) para buscar e editar texto
- [Apache POI](https://poi.apache.org/) para planilhas, empacotado para Android no módulo `poishadow` (receita do [poi-on-android](https://github.com/centic9/poi-on-android))
- Storage Access Framework para abrir arquivos (sem permissão de armazenamento)
- minSdk 26 · targetSdk 36

## Licença

Distribuído sob a [Licença Apache 2.0](LICENSE).
