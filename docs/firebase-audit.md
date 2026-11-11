# Auditoría Firebase de Mercadeo Comitán

## Estado confirmado

- Proyecto: `mercadeo-comitan-1819c`.
- Aplicación Android: `com.cabgon.mercadeocomitan`.
- Servicios usados por el cliente: Firebase Authentication, Realtime Database, Cloud Storage, Firebase Cloud Messaging y App Check con Play Integrity.
- No hay SDK de Firestore, Cloud Functions, Crashlytics ni Remote Config en la aplicación.
- `firebase.json` declara un código de Functions en `functions/`, pero esa carpeta no existe en el proyecto.
- No hay archivos de reglas (`database.rules.json`, `storage.rules`), índices ni configuración de Firebase Emulator en el repositorio.

## Rutas de datos que deben conservarse durante la migración

| Ruta | Uso actual |
| --- | --- |
| `Usuarios/{uid}` | Perfil, rol, estado, tokens FCM, favoritos, permisos de publicación y compra |
| `Anuncios/{id}` | Publicaciones, ubicación, estado, vistas y `Imagenes/{id}` |
| `Chats/{chatId}` | Participantes, typing y `mensajes/{id}` |
| `ComentariosVendedores/{id}` | Calificaciones y comentarios |
| `Ordenes/{id}` | Pedidos, estado, historial y transferencia |
| `CuentasTransferencia/{uid}` | Datos de transferencia del vendedor |
| `configApp/mensajeGlobal` | Aviso global administrable |

Las pantallas renovadas siguen leyendo y escribiendo estas rutas. El cambio de interfaz no requiere renombrar colecciones ni mover datos.

## Riesgos que requieren trabajo de backend

1. Las reglas no están versionadas. La app no puede considerarse segura hasta auditar y desplegar reglas de Realtime Database y Storage desde un entorno de pruebas.
2. `canPost`, roles y bloqueos no pueden confiar en escrituras del cliente. La concesión de una compra debe verificarse con Google Play Developer API mediante una función de servidor y una cuenta de servicio restringida.
3. Las vistas deben incrementarse con una operación atómica (`ServerValue.increment(1)`), no con lectura seguida de escritura.
4. Los tokens FCM deben almacenarse por dispositivo (`Usuarios/{uid}/fcmTokens/{tokenId}`) para no invalidar sesiones en otros teléfonos.
5. Storage necesita validar autenticación, propietario, tamaño y MIME; las imágenes de chat y anuncios deben tener rutas separadas.

## Migración recomendada

1. Exportar una copia de Realtime Database y Storage antes de modificar reglas.
2. Crear reglas y Functions en un proyecto de staging y probarlas con Firebase Emulator Suite.
3. Añadir una función de verificación de compras que escriba un `entitlements/{uid}` firmado por servidor; mantener `canPost` sólo como campo de compatibilidad durante una ventana de migración.
4. Migrar tokens a `fcmTokens` manteniendo temporalmente `fcmToken` para versiones antiguas.
5. Desplegar reglas con una revisión de lectura/escritura por ruta y activar App Check enforcement después de validar instalaciones existentes.
6. Eliminar campos de compatibilidad sólo cuando la versión mínima instalada ya conozca el nuevo contrato.

Este documento describe el estado observado en el repositorio. No afirma que existan reglas remotas ni despliega cambios en el proyecto Firebase.
