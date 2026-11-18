# FaunaGBIF

Aplicación Android nativa en **Kotlin** y **Jetpack Compose** para la identificación taxonómica automática de **Flora (`Plantae`)** y **Fauna (`Animalia`)** a partir de fotografías del dispositivo, integrando **Firebase AI Logic (Gemini)**, la base de datos taxonómica global de **GBIF**, criterios de la **Lista Roja de la UICN** y exportación directa de fichas técnicas en **PDF** con visor integrado.

---

## Características principales

- **Selector de archivos sin permisos intrusivos:** Carga imágenes desde el explorador de archivos del sistema (`OpenDocument`), la galería fotográfica (`PickVisualMedia`) o el historial reciente guardado en la app.
- **Identificación automática Flora / Fauna:** Análisis visual multimodal con `gemini-3.8-flash` y Firebase App Check que detecta automáticamente el reino y completa las 4 secciones de la ficha técnica correspondiente.
- **Validación en vivo con GBIF (`api.gbif.org/v1`):** Verificación del taxón (`usageKey`), jerarquía completa (Reino, Filo, Clase, Orden, Familia, Género), autoría científica y conteo global de ocurrencias.
- **Datos de campo editables:** Permite completar código de ficha, fecha, observador, institución, hora y coordenadas GPS antes de exportar.
- **Exportación y visor PDF integrado:** Genera documentos PDF A4 multipágina (`PdfDocument`), los guarda en *Descargas* o ubicación personalizada, incluye un **visor PDF integrado (`PdfRenderer`)** para previsualizar todas las páginas sin depender de apps externas y permite compartir el archivo.

---

## Publicación segura en GitHub y descarga del APK

Por seguridad, `app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml` y los archivos `.keystore` / `.jks` están excluidos en `.gitignore`. En su lugar se incluyen las plantillas `app/google-services.example.json` y `app/firebase_applet_config.example.xml`.

### 1. Subir el proyecto a GitHub sin exponer claves

Si tu repositorio local ya tenía rastreados archivos de Firebase antes de actualizar `.gitignore`, desvincula el seguimiento sin borrarlos de tu equipo:

```bash
git rm --cached app/google-services.json app/src/main/res/values/firebase_applet_config.xml
```

### 2. Configurar GitHub Secrets para compilar en la nube

En tu repositorio de GitHub ve a **Settings → Secrets and variables → Actions → New repository secret** y añade:

| Secreto | Descripción | Obligatorio |
| --- | --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | Contenido de `app/google-services.json` codificado en Base64 (`base64 -w 0 app/google-services.json`) | Recomendado para Gemini real |
| `FIREBASE_APPLET_CONFIG_BASE64` | Contenido de `app/src/main/res/values/firebase_applet_config.xml` en Base64 | Recomendado para App Check Release |
| `ANDROID_KEYSTORE_BASE64` | Keystore `.jks` de producción en Base64 | Opcional (para APK Release) |
| `STORE_PASSWORD` | Contraseña del keystore Release | Opcional (para APK Release) |
| `KEY_PASSWORD` | Contraseña del alias `upload` | Opcional (para APK Release) |

> Si no configuras `GOOGLE_SERVICES_JSON_BASE64`, GitHub Actions usará automáticamente las plantillas `.example` para validar compilación, Lint y pruebas unitarias.

### 3. Descargar el APK compilado desde GitHub

El flujo `.github/workflows/android-apk.yml` se ejecuta automáticamente al hacer `push`:

- **Desde GitHub Actions (Artifacts):** Abre la pestaña **Actions** → selecciona la ejecución más reciente → descarga **FaunaGBIF-debug-apk** (o **FaunaGBIF-release-apk**).
- **Desde GitHub Releases:** Publica una etiqueta de versión (ej. `git tag v1.0.0 && git push origin v1.0.0`) o ejecuta manualmente **Run workflow** marcando `Publicar APK en GitHub Releases = true`. El archivo `.apk` quedará adjunto para descarga directa en la sección **Releases**.

---

## Compilación local

```bash
# Restaurar plantillas si es un clon limpio y no tienes credenciales propias
cp app/google-services.example.json app/google-services.json
cp app/firebase_applet_config.example.xml app/src/main/res/values/firebase_applet_config.xml

# Ejecutar pruebas y compilar APK Debug
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
