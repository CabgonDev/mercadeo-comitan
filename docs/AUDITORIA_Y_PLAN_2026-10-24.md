# MercadeoComitan — Auditoría técnica y plan de mejora

**Fecha:** 24 de octubre de 2026  
**Alcance:** proyecto Android recibido en `MercadeoComitan.zip`; revisión de código, configuración, datos, permisos, funcionalidad, rendimiento, ciclo de vida y recursos de interfaz.  
**Método:** análisis estático de archivos Kotlin, XML, Gradle y configuraciones Firebase; comparación de fuente original con copia corregida. **No** se accedió a Firebase remoto, Play Console ni dispositivos Android; **no** se realizó pentest dinámico, compilación Android ni despliegue.

## Resumen ejecutivo

El proyecto es **recuperable y merece evolución incremental**, no reescritura. Su base es Android nativo con Kotlin, Activities/Fragments, ViewBinding/XML, Firebase Authentication, Realtime Database, Storage, Firebase Cloud Messaging, App Check con Play Integrity, Google Play Billing, anuncios y geolocalización. Integra publicación de anuncios, búsqueda, favoritos, chat, perfiles, calificaciones, pedidos y panel administrativo.

**Conclusión:** la seguridad de producción **no puede certificarse** porque no se entregaron reglas versionadas de Firebase ni una implementación del backend que verifica compras y autoriza acciones privilegiadas. Se preparó una **copia de código con mitigaciones locales** para riesgos acotados, sin romper deliberadamente el contrato de datos ni introducir reglas remotas no probadas.

### Inventario

| Componente | Observación |
|---|---|
| Plataforma | Android Kotlin, interfaz XML + ViewBinding, un módulo `app` |
| Configuración | `applicationId`: `com.cabgon.mercadeocomitan`; `versionName`: `2.0.2` |
| SDK | `minSdk 24`, `compileSdk 37`, `targetSdk 37` |
| Herramientas | Java/Kotlin JVM target 17; AGP 9.4.1; Billing 9.1.0; Firebase BoM 35.0.2 según archivos Gradle |
| Código revisado | 55 archivos Kotlin y 153 XML de `app/src/main/res`; 3 archivos de pruebas existentes |
| Firebase | Authentication, Realtime Database, Storage, FCM y App Check. `firebase.json` referencia una carpeta `functions/` que no aparece en el ZIP |
| Empaquetado recibido | ZIP de aproximadamente 281 MiB comprimidos, con cachés, `node_modules` y `app-release.aab` ya construido; no son necesarios para revisar ni distribuir la fuente de mejoras |

## Hallazgos priorizados

Las referencias de código son rutas relativas a `app/src/main/java/com/cabgon/mercadeocomitan/` salvo que se indique lo contrario. Las líneas corresponden a la **versión original** y pueden variar en la copia corregida.

| ID | Severidad | Evidencia y consecuencia | Estado |
|---|---|---|---|
| MC-01 | **Crítica, pendiente** | No hay reglas `database.rules.json` o `storage.rules` versionadas; no es posible auditar permisos de lectura/escritura ni índices desde esta entrega. Las reglas efectivas de producción son desconocidas. | Requiere exportar y probar reglas del proyecto remoto |
| MC-02 | **Crítica, pendiente** | `billing/BillingHelper.kt:130–170, 207–226` reconoce compras y establece `Usuarios/{uid}/canPost` y tokens desde el cliente. `Anuncios/CrearAnuncio.kt:176–203, 251–281` también concede permiso desde el dispositivo. La comprobación de compra/entitlement no está avalada por un servidor confiable. | Backend de verificación Play necesario |
| MC-03 | **Crítica, pendiente** | `Eliminar_cuenta.kt:79–90` borra Firebase Auth primero y luego intenta borrar anuncios/perfil mediante una consulta con listener persistente. Una pérdida de sesión o error de reglas deja datos huérfanos; tampoco se ve barrido de Storage/chats/pedidos. | Migrar a solicitud y proceso de eliminación de servidor |
| MC-04 | **Alta, pendiente** | `PanelAdminActivity.kt:38–58` determina privilegios leyendo `Usuarios/{uid}/rol` y la UI de administración escribe roles/bloqueos directamente. Sin reglas verificadas, no se puede garantizar quién modifica esos campos. | Custom claims/servidor + reglas restrictivas |
| MC-05 | **Alta, pendiente** | `Fragmentos/FragmentCuenta.kt:148–155` comprueba `bloqueado` al abrir Cuenta; no hay una verificación integral de bloqueo respaldada por reglas en cada operación. | Reglas de denegación para bloqueados y revocación de sesión |
| MC-06 | **Alta, pendiente** | Los perfiles en `Usuarios/{uid}` agrupan información visible, token(es) FCM, `purchase/token`, favoritos, permisos y atributos privados. Hay lecturas de perfil de otros usuarios; falta separación y comprobación de privacidad de campos. | Perfil público/privado y restricciones por campo |
| MC-07 | **Media, mitigada parcialmente** | `Chat/ChatActivity.kt` inicializaba `chatId` después de un posible `finish()`, pero limpiaba listeners usando esa propiedad en `onDestroy()`. Asimismo, registraba listener sobre `Query` y lo eliminaba de `DatabaseReference`. | Guardas de inicialización y eliminación de Query aplicadas |
| MC-08 | **Media, mitigada parcialmente** | `PanelAdminActivity.kt:220–237, 283–295, 323–335` llamaba `ref.child(email)` antes de decidir si era email o UID. El caracter `.` no se admite como clave en Realtime Database. | Resolución segura por email/UID aplicada; autorización remota sigue pendiente |
| MC-09 | **Media, mitigada parcialmente** | `Fragmentos/FragmentInicio.kt:354–387` contiene consultas iniciales asíncronas y listeners en tiempo real. Una respuesta antigua puede sustituir filtros recientes; el banner debe liberarse con la vista. | Invalidación de consulta y ciclo de vida de AdView aplicados |
| MC-10 | **Media, pendiente** | `Adaptadores/AdaptadorAnuncio.kt:109–160` consulta datos por tarjeta para favoritos e imágenes. `FragmentInicio` solo carga una ventana de los 120 anuncios recientes; `ChatActivity` no pagina el historial de mensajes. | Requiere caché, consulta agregada, paginación y DiffUtil |
| MC-11 | **Media, corregida localmente** | `MainActivity.kt:264–271` escribía tokens FCM completos en Logcat; `Constantes.kt:155–165` actualizaba vistas con lectura+escritura no atómicas; registro por email omitía algunos valores base de seguridad. | Logs retirados, incremento atómico y perfil inicial homogéneo |
| MC-12 | **Media, corregida localmente** | El manifiesto permitía backup y los archivos de exclusión estaban vacíos. Varias acciones iconográficas no tenían descripción accesible; el catálogo no presentaba estado vacío contextual. | Respaldo deshabilitado; iconos etiquetados y estado vacío añadido |
| MC-13 | **Media, pendiente** | No se observó paginación o tratamiento centralizado de errores/estado para todas las consultas en tiempo real; `notifyDataSetChanged()` se usa con frecuencia. | Refactor incremental de repositorios y actualizaciones de listas |
| MC-14 | **Media, pendiente** | `firebase.json` declara Functions, pero no se entregó la carpeta; tampoco hay pruebas de Security Rules con Emulator Suite. | Infraestructura reproducible y CI necesarios |

**Interpretación:** “Pendiente” significa que **no fue corregido** y puede condicionar el lanzamiento. La ausencia de archivos de reglas **no demuestra** por sí sola que la base remota sea pública; indica que el repositorio recibido no permite comprobarla. App Check y autenticación **no sustituyen** las reglas de autorización del servidor.

## Cambios seguros incorporados en la copia de fuente

1. **Privacidad del dispositivo:** `AndroidManifest.xml` limita tráfico no cifrado (`usesCleartextTraffic=false`) y desactiva copia de seguridad (`allowBackup=false`); se añadieron exclusiones en las políticas de extracción de datos. *Confirmar compatibilidad con todos los servicios consumidos*.
2. **Favoritos y claves Firebase:** `Constantes.kt` evita `uid!!`, rechaza IDs inválidos, muestra errores de escritura y utiliza HTTPS para la navegación de Maps.
3. **Contador de vistas:** operación atómica `ServerValue.increment(1L)` en lugar de lectura seguida de sobrescritura; las reglas remotas deben autorizar esa operación de forma limitada.
4. **Chat:** valida UID de destinatario, protege acceso a propiedades no inicializadas, elimina el listener desde la misma `Query`, controla notificaciones durante el chat visible, limita mensajes de texto a 4,000 caracteres.
5. **Catálogo:** descarta respuestas viejas con un contador de versión; libera banner publicitario al destruir la vista; añade indicación de catálogo sin resultados y búsqueda sin coincidencias.
6. **Panel administrativo:** distingue UID de email (sin crear rutas inválidas), verifica IDs de anuncios y muestra éxito únicamente después de una respuesta exitosa de escritura. **No cambia la seguridad del rol en servidor.**
7. **Facturación (solo UX):** distingue éxito/error real de las tareas Firebase y libera la conexión Billing al destruir la actividad principal. **No verifica compras en backend** y no debe interpretarse como solución al riesgo MC-02.
8. **Registro y anuncios:** inicializa nuevos perfiles con rol de usuario y permisos básicos denegados; valida límites de texto, precio finito y positivo.
9. **Accesibilidad/orden:** añade descripciones a botones de íconos, mejora el estado vacío de búsqueda y amplía `.gitignore` para evitar distribuir cachés, credenciales locales y builds.

No se renombraron rutas Firebase, no se modificaron datos de producción ni se sustituyeron pantallas o navegación existentes. El ZIP mejorado contiene **código fuente**, no una APK/AAB compilada o firmada.

## Plan de seguridad para producción

### Fase P0 — Bloqueante antes de lanzar

**A. Reglas Firebase y permisos**

- Exportar las reglas reales de RTDB y Storage; guardar copia y crear staging. Versionar reglas e índices en la rama de desarrollo.
- `Usuarios/{uid}`: propietario edita solo campos no privilegiados; `rol`, `bloqueado`, `canPost`, `puedePublicarGratis`, `purchase/*` y campos de servidor no deben ser editables por clientes ordinarios.
- `Anuncios/{id}`: escritura, edición y borrado limitados al dueño autorizado; validar tipos, tamaño, precio, contenido, referencias de usuario y estados.
- `Chats/{chatId}/mensajes`: lectura/escritura solo entre participantes validados; UID emisor desde Auth, tipos y tamaños acotados, estados de entrega limitados.
- `Ordenes/{id}` y `CuentasTransferencia/{uid}`: lectura/escritura restringida por rol/participación; bloquear transiciones de estado no autorizadas y exposición de datos bancarios.
- `configApp/mensajeGlobal`: escritura solo desde servidor/administrador avalado por reglas.
- Storage: restringir rutas de anuncios/chats por propietario/participantes, tamaño, MIME, acceso y eliminación; no basarse únicamente en restricciones Android.
- Crear pruebas positivas y negativas de reglas en **Firebase Emulator Suite**: usuario no autenticado, propietario, tercero, bloqueado, admin y superadmin.

**B. Compras**

- El cliente envía `purchaseToken`, `productId`, UID y versión de compra a un endpoint autenticado de **Cloud Functions/Cloud Run**.
- El servidor consulta **Google Play Developer API**, valida estado/propietario/producto, evita reuso y duplicación de tokens, concede una autorización (`entitlements/{uid}`) y reconoce la compra según el flujo oficial.
- Restringir escrituras a `entitlements` y compra confirmada a identidad de servidor; recuperar derechos al reiniciar o reinstalar, incluida revocación/reembolso.
- Migrar `canPost` de campo editable del cliente a valor derivado de autorización verificada, con compatibilidad temporal para versiones previas.

**C. Eliminación de cuenta y bloqueos**

- Implementar proceso idempotente desde backend con autenticación reciente, estado `deletionPending`, borrado verificable de referencias y Storage, tratamiento documentado de pedidos que deban retenerse, y borrado Auth **al final**. Informar al usuario de resultados/reintentos.
- Aplicar bloqueos y revocaciones en reglas y/o verificación de token, no solo en pantallas.

### Fase P1 — Funcionamiento y fluidez

- Paginación estable del catálogo y chat (`limitToLast` con cursores apropiados); índices `.indexOn` por `tiempo`, `uid`, `email`, `compradorId`, `vendedorId` y consultas observadas.
- Reducir consulta por tarjeta: resumen de imágenes/miniaturas en anuncio, caché y cargas Glide; carga agregada de favoritos por usuario y actualizaciones por DiffUtil/ListAdapter.
- Cancelar listeners, callbacks y observadores en el ciclo de vida correcto; evitar escrituras de presencia con cada `onPause()` y usar `onDisconnect()` + estado de proceso.
- Manejo consistente de carga, vacío, sin conexión, reintentos y errores; evitar diálogos permanentes/obsoletos; centralizar mensajes y estados.
- Añadir métricas de arranque, uso de datos, scroll y estabilidad (Android Vitals/Crashlytics si se incorpora y se revisa consentimiento).

### Fase P2 — Interfaz y arquitectura

- Migrar gradualmente consultas de pantallas a repositorios ViewModel + StateFlow/Livedata para separar Firebase de la UI, sin imponer reescritura a Jetpack Compose.
- Mejorar jerarquía visual del catálogo: precios legibles, cards uniformes, estados de inventario, filtros persistentes, indicadores de confianza y skeletons discretos.
- Revisar accesibilidad completa: TalkBack en pantallas, contraste, foco/orden de navegación, targets táctiles de 48dp, tipografía escalable y mensajes accesibles.
- Validar diseños con teléfonos pequeños/grandes, tema claro/oscuro, navegación por gestos, conexiones lentas y sin conexión.
- Limitar permisos a las funcionalidades estrictamente necesarias y revisar claves de servicios en Google Cloud Console (restricción de Maps por paquete/certificado SHA/API; `google-services.json` es configuración pública, no credencial de administrador).

## Matriz mínima de pruebas antes de publicación

| Categoría | Escenario esperado |
|---|---|
| Login y sesiones | Email y Google, sesión caducada, usuario bloqueado, navegación sin crash |
| Roles | Usuario normal no puede conceder privilegios ni editar datos privilegiados desde SDK/REST |
| Compras | Compras nuevas, canceladas, pendientes, reembolsadas, repetidas, restauradas; autorización solo del servidor |
| Publicaciones | Crear/editar/eliminar propias; rechazar modificar las de otro UID; imágenes faltantes/cargas fallidas |
| Chat | 2 usuarios, chat externo inválido, offline, mensajes largos, fotos, paginación, notificación en chat abierto |
| Catálogo | Cambiar categorías rápidamente, filtros simultáneos, sin anuncios, más de 120 anuncios, scroll continuo |
| Cuenta | Eliminación con datos asociados y fallos intermedios; limpieza completa, sin registros huérfanos |
| Interfaz | TalkBack, rotación, cambio de tema, tamaño de fuente y pruebas en pantalla pequeña |
| Producción | Reglas por ruta en emulador, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, pruebas físicas |

Comandos sugeridos desde un equipo con **Android SDK y Java 17**:

```bash
python3 tools/verificar_codigo.py
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

**No ejecutar `firebase deploy` sobre producción** antes de auditar reglas, validar backend y preparar rollback.

## Verificación realmente ejecutada

- Comparación de archivo por archivo entre copia fuente y fuente original.
- Parseo de **154 XML** de `app/src/main` (153 recursos + manifiesto), sin errores sintácticos.
- Confirmación del nombre/clase de servicio FCM con el manifiesto.
- Verificación de descripciones en todos los `ImageButton` de layouts y existencia de cadenas nuevas.
- Comprobaciones estáticas de `allowBackup`, tráfico sin cifrar, guardas de chat, resultado de compras, contador atómico y ausencia de logs con tokens FCM completos.
- **19 de 19 comprobaciones locales aprobadas** en `tools/verificar_codigo.py`. Son verificaciones de estructura, **no tests de integración**.

**Limitaciones expresas:** no se compiló ni ejecutó la aplicación Android por falta de SDK y entorno de emulación en esta sesión; no se probaron reglas remotas ni Google Play Console; no se certifica que el APK final compile ni que los cambios estén listos para producción sin la matriz de pruebas anterior.

## Recomendación de liberación

Mantener una rama de seguridad para estos cambios. Aplicar y revisar el parche fuente mediante code review, compilar, pasar pruebas y distribuir primero a un canal interno/cerrado. **No lanzar públicamente** sin completar MC-01, MC-02, MC-03, MC-04 y MC-05 y sin validar las reglas remotas. Respaldar datos, mantener rollback y medir errores antes de ampliar el despliegue.

### Documentación oficial para implementación

- Firebase Security Rules (Realtime Database): https://firebase.google.com/docs/database/security
- Firebase Emulator Suite: https://firebase.google.com/docs/emulator-suite
- Firebase Storage Security Rules: https://firebase.google.com/docs/storage/security
- Seguridad de Google Play Billing: https://developer.android.com/google/play/billing/security
- Guía de backups Android: https://developer.android.com/identity/data/autobackup

---

*Documento generado a partir del ZIP entregado. El proyecto original no fue sobrescrito. Los cambios descritos están en el ZIP de mejoras de fuente y el archivo de diferencias adjunto.*
