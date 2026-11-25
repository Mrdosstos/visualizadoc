# VisualizaDoc

App Android gratuito e sem anúncios para visualizar e editar documentos.

| Formato | Situação |
|---|---|
| PDF | ✅ Visualizar (rolagem, zoom com pinça) |
| Planilhas (.xlsx, .xls, .csv) | 🔜 Visualizar e editar (fase 2) |
| Word (.docx) | 🔜 Visualizar (fase 3) e editar (fase 4) |

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
- `PdfRenderer` nativo do Android para PDF
- Storage Access Framework para abrir arquivos (sem permissão de armazenamento)
- minSdk 26 · targetSdk 36

## Licença

Distribuído sob a [Licença Apache 2.0](LICENSE).
