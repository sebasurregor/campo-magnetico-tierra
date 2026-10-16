# Campo magnético de la Tierra — app de laboratorio (Física 2)

App Android sencilla para medir el campo magnético terrestre con el magnetómetro del celular y
compararlo con los valores teóricos (plan de trabajo: WMM 2025, Medellín, octubre de 2026, y el
modelo geomagnético que trae Android).

## Qué hace la app

Tiene tres pestañas:

- **En vivo**
  - Bx, By, Bz y |B| del magnetómetro (μT), unas 10 veces por segundo, con la precisión del sensor.
  - Los valores crudos y el *bias* que estima Android, si el teléfono tiene el sensor sin calibrar.
  - El campo pasado al marco terrestre: X, Y, Z, H, F, I y B∥.
  - Los valores teóricos del plan y del modelo de Android, uno al lado del otro.
  - Una alerta de interferencia cuando corresponde.
- **Capturas**
  - Captura promediada de 3 s (promedio ± desviación estándar), etiquetada con la orientación.
  - Tabla medido vs. teórico con diferencia y error porcentual.
  - Asistente de desfase con giro de 180°.
  - Exportación de todas las capturas a CSV.
- **Configuración**
  - Ubicación: aproximada del teléfono, escrita a mano o la de Medellín por defecto.
  - Fuente teórica para comparar: valores del plan (por defecto) o modelo de Android.
  - Edición de los valores del plan.
  - Redondeo de coordenadas en el CSV.

## Descargar e instalar el APK

1. En el celular, abre este enlace en el navegador:
   **https://github.com/sebasurregor/campo-magnetico-tierra/releases/download/latest/app-debug.apk**
   (también está en la sección *Releases* del repositorio, como "Última versión (debug)").
2. Al abrir el archivo descargado, Android te pedirá permitir **"instalar apps desconocidas"** para
   el navegador (o el gestor de archivos) que usaste. Actívalo **solo para esta instalación**.
3. Instala la app.
4. **Desactiva el permiso otra vez**: *Ajustes → Aplicaciones → (tu navegador) → Instalar apps
   desconocidas → No permitir*. La ruta exacta cambia un poco según la marca del teléfono.
5. (Opcional) Comprueba que el archivo es el que compiló GitHub. Compara su SHA-256 con el que
   aparece en la descripción del Release. En Windows: `certutil -hashfile app-debug.apk SHA256`.

> **Si al actualizar sale "App no instalada"**: desinstala primero la versión anterior. Cada
> compilación de debug en GitHub Actions puede firmarse con una clave distinta, y Android no deja
> instalar encima una app con otra firma. Las capturas guardadas en la app se pierden al
> desinstalar; si las necesitas, expórtalas antes a CSV.

## Cómo hacer cada experimento

Antes de empezar:

- Aleja el teléfono de objetos metálicos, imanes, parlantes, computadores y cables con corriente.
  Usa una mesa de madera o de plástico. Quita la funda si tiene imán.
- Mueve el teléfono en forma de **8** unas cuantas veces para que Android calibre el magnetómetro,
  hasta que la precisión sea **Media** o **Alta**.
- En *Configuración*, revisa la ubicación y elige con qué valores teóricos comparar.

### 1. Plano, con el eje y hacia el norte geográfico

1. Pon el teléfono plano sobre la mesa, pantalla arriba.
2. Gíralo hasta que su parte superior (eje y) apunte al **norte geográfico**. Ojo: no es el norte
   de la brújula. El norte geográfico está |D| ≈ 8° al **este** del norte magnético, porque en
   Medellín D ≈ −8° (oeste).
3. En *Capturas* elige **"Plano, y al norte"** y toca **Capturar**. No toques el teléfono durante
   los 3 s.
4. La tabla compara F, H, Z, I y B∥, y además Bx, By, Bz con lo esperado: **Bx ≈ Y**,
   **By ≈ X** y **Bz ≈ −Z**.

### 2. Rotación sobre el eje z

1. Con el teléfono plano sobre la mesa, gíralo sobre la mesa en pasos (por ejemplo, cada 45° o 90°).
2. En cada posición, elige **"Rotación eje z"**, escribe el ángulo en *Nota* (por ejemplo "90°") y
   captura.
3. **Qué observar:** Bx y By cambian (como un seno y un coseno), mientras que Bz, |B| y los valores
   del marco terrestre (F, H, Z, I) casi no cambian.

### 3. Rotación sobre el eje x

1. Gira el teléfono alrededor de su eje x (el eje que va de izquierda a derecha de la pantalla),
   levantando la parte superior: plano, inclinado, vertical, etc.
2. En cada posición, elige **"Rotación eje x"**, escribe el ángulo en *Nota* y captura.
3. **Qué observar:** cambian By y Bz y Bx casi no cambia. |B| y los valores del marco terrestre
   deben mantenerse.

### 4. Rotación sobre el eje y

1. Gira el teléfono alrededor de su eje y (el eje que va de abajo hacia arriba de la pantalla),
   inclinándolo hacia un lado.
2. En cada posición, elige **"Rotación eje y"**, escribe el ángulo y captura.
3. **Qué observar:** cambian Bx y Bz y By casi no cambia. |B| y el marco terrestre se mantienen.

### 5. Asistente de desfase (giro de 180°)

1. Pon el teléfono plano sobre la mesa y toca **Capturar 0°**.
2. Sin levantarlo ni voltearlo, gíralo **180°** sobre la mesa y toca **Capturar 180°**.
3. La app calcula, para x e y, el campo real y el desfase (*offset*) del sensor. Si el teléfono
   tiene sensor sin calibrar, también hace el cálculo con los valores crudos y lo compara con el
   *bias* que estima Android.
4. Este giro **no corrige z**: al girar sobre la mesa, z no cambia de signo, así que el campo en z
   no se puede separar de su desfase.

### Exportar

En *Capturas*, toca **Exportar CSV** y elige WhatsApp, Drive, correo, etc. El archivo usa coma
como separador y punto como decimal. En Excel: *Datos → Desde texto/CSV → Delimitador: coma*. En
Google Sheets se importa directamente.

## Qué significa cada valor

| Valor | Significado |
|---|---|
| Bx, By, Bz | Campo medido en los ejes del teléfono (μT). Con el teléfono plano y pantalla arriba: x a la derecha, y hacia la parte superior, z saliendo de la pantalla. |
| \|B\| | Magnitud del campo medido. |
| Precisión | Estado del sensor: Alta / Media / Baja / No confiable (`SENSOR_STATUS_ACCURACY_*`). |
| Crudo y bias | Lectura sin calibrar y desfase (hierro duro) que estima Android. Crudo − bias ≈ lectura calibrada. |
| X | Componente norte (geográfico) del campo (μT). |
| Y | Componente este (μT); negativa = hacia el oeste. |
| Z | Componente vertical, positiva hacia **abajo** (μT). |
| H | Intensidad horizontal. |
| F | Intensidad total. |
| I | Inclinación: ángulo del campo bajo el horizonte (positiva hacia abajo). |
| D | Declinación: ángulo entre el norte magnético y el geográfico (positiva hacia el este). |
| B∥ | Componente del campo paralela al eje de rotación de la Tierra. |
| ± | Desviación estándar de las muestras de la captura de 3 s. |
| \|Dif.\| y Error | \|medido − teórico\| (μT o °) y error porcentual. |

## Fórmulas usadas

- H = √(X² + Y²)
- F = √(X² + Y² + Z²)
- I = atan(Z / H)
- D = atan2(Y, X)
- B∥ = X·cos(φ) − Z·sin(φ), donde φ es la latitud geográfica.
- Teléfono plano, pantalla arriba y eje y al norte geográfico: Bx = Y, By = X, Bz = −Z.
- Promedio x̄ = Σxᵢ / n y desviación estándar muestral s = √( Σ(xᵢ − x̄)² / (n − 1) ).
- Error porcentual = |medido − teórico| / |teórico| × 100.
- Giro de 180°: B(0°) = B + b y B(180°) = −B + b, para x e y. Entonces:
  - B_real = (B(0°) − B(180°)) / 2
  - desfase b = (B(0°) + B(180°)) / 2

### Paso al marco terrestre (sin importar la orientación del teléfono)

1. Con el sensor de gravedad (o el acelerómetro filtrado), `SensorManager.getRotationMatrix`
   entrega la matriz R que lleva la lectura del teléfono al marco (Este, Norte, Arriba):
   (E, N, U) = R · (Bx, By, Bz).
2. **Importante:** ese "Norte" es el **norte magnético**. Android lo define como la dirección
   horizontal del propio campo medido, así que E sale ≈ 0. Para pasar al norte geográfico se gira
   el plano horizontal un ángulo igual a la declinación teórica D:
   - X = N·cos D − E·sin D
   - Y = N·sin D + E·cos D
   - Z = −U
3. Por eso la app **no mide D**: la toma de la fuente teórica elegida. Como control, también se
   muestra la inclinación de `SensorManager.getInclination`, con el signo cambiado para que sea
   positiva hacia abajo.

### Valores teóricos

- **Valores del plan** (por defecto, editables): X = 26.6 μT, Y = −3.7 μT, Z = 15.1 μT,
  H = 26.9 μT, F = 30.8 μT, I = 29.3°, D = −8.0°, B∥ = 24.8 μT.
  Son del WMM 2025 para Medellín (lat 6.2442, lon −75.5812, 1495 m), octubre de 2026.
- **Modelo de Android** (`android.hardware.GeomagneticField`): se calcula con la ubicación y la
  fecha actuales. Su resultado depende de la versión del modelo que trae el Android de cada
  teléfono. Por eso se muestra junto a los del plan: comparar ambos con lo medido sirve para la
  discusión de resultados.

## Privacidad

- **Permisos:** la app solo usa **ubicación aproximada** (`ACCESS_COARSE_LOCATION`), y solo cuando
  tocas "Usar ubicación aproximada del teléfono" con la app abierta. No usa ubicación en segundo
  plano, ubicación precisa, cámara, micrófono, contactos ni almacenamiento. Si niegas el permiso,
  la app funciona igual con las coordenadas por defecto o las que escribas a mano.
- **Sin internet:** la app **no tiene permiso de internet**. Funciona 100 % sin conexión y no puede
  enviar datos a ningún servidor. No tiene analíticas, anuncios, Firebase ni SDKs de terceros.
  Solo usa librerías oficiales de AndroidX/Jetpack y Kotlin.
- **Datos:**
  - Las capturas y los ajustes se guardan solo en el almacenamiento privado de la app.
  - La copia de seguridad está desactivada (`allowBackup="false"`).
  - El CSV solo se crea cuando tocas "Exportar", y solo lo recibe la app que elijas para
    compartirlo.
  - Por defecto, las coordenadas del CSV se redondean a 2 decimales (~1 km).
- **Cómo comprobarlo en el teléfono:**
  1. *Ajustes → Aplicaciones → Campo Magnético → Permisos*. Solo debe aparecer **Ubicación**, y
     como "aproximada" o "solo mientras se usa la app".
  2. En la misma pantalla de la app, en *Permisos* o en *Datos móviles y Wi-Fi*, no aparece acceso a
     internet: la app no lo declara. En algunos teléfonos se ve en *Ajustes → Aplicaciones → Campo
     Magnético → Todos los permisos* o *Detalles de permisos*. No aparece "Acceso completo a la
     red".
  3. Prueba en **modo avión**: todo funciona igual.
- **Verificación automática:** cada compilación en GitHub Actions lista los permisos del APK con
  `aapt2` (se ven en el log). La compilación **falla** si aparece cualquier permiso distinto de
  `ACCESS_COARSE_LOCATION`, si hay algún componente exportado además de la Activity principal o si
  `allowBackup` no es `false`. El SHA-256 del APK se publica en la descripción del Release.

## Compilación (sin Android Studio)

Todo se compila en GitHub Actions (`.github/workflows/build-apk.yml`) en cada push a `main`:

1. Corre las pruebas unitarias (`MagMathTest`) y compila el APK de debug con
   `gradle test assembleDebug`. Usa JDK 17, Gradle 9.6.1 y AGP 9.4.1, con compileSdk y
   targetSdk 37.
2. Verifica los permisos y componentes del APK (`.github/scripts/verificar_apk.py`).
3. Sube `app-debug.apk` como artifact.
4. Publica o actualiza el Release `latest` con el APK y su SHA-256.

Seguridad del workflow:

- El permiso por defecto es `contents: read`. Solo el job que publica el Release tiene
  `contents: write`.
- Cada action está fijada a un commit SHA completo.
- No hay secretos.
- No se ejecuta en pull requests ni en forks.

## Estructura

```
app/src/main/java/com/fisica2/campomagnetico/
  MagMath.kt        física pura (probada con JUnit)
  Capture.kt        capturas, promedios y comparación
  CsvFormat.kt      formato del CSV
  SensorReader.kt   magnetómetro, sin calibrar y gravedad
  LocationReader.kt ubicación aproximada (una sola vez)
  TheoryModel.kt    GeomagneticField
  Settings.kt, CaptureStore.kt, CsvExporter.kt, MainActivity.kt
  ui/               pantallas en Jetpack Compose
app/src/test/.../MagMathTest.kt
```
