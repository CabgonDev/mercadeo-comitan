# 🛒 Mercadeo Comitán

**Versión:** 3.0.6  
**Plataforma:** Android  
**Lenguaje:** Kotlin 100%  

Aplicación de marketplace local para compra y venta de productos en Comitán, Chiapas.

---

## 📱 Características Principales

- 🏪 **Marketplace Local** - Compra y venta entre usuarios de Comitán
- 📸 **Publicación de Anuncios** - Hasta 8 fotos, categorización, ubicación GPS
- 💬 **Chat en Tiempo Real** - Comunicación directa entre comprador y vendedor
- 📍 **Búsqueda por Ubicación** - Filtrado por distancia (hasta 40km)
- ⭐ **Sistema de Favoritos** - Guarda anuncios para ver después
- 🔔 **Notificaciones Push** - Alertas de mensajes y actividad
- 🔐 **Autenticación Segura** - Login con Email y Google

---

## 🎨 Stack Tecnológico

### Core
- **Lenguaje:** Kotlin
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 37 (Android 14)
- **Arquitectura:** Activities + Fragments (migración a MVVM en progreso)

### Firebase
- **Realtime Database** - Almacenamiento de anuncios y mensajes
- **Storage** - Imágenes y multimedia
- **Authentication** - Email/Password, Google Sign-In
- **Cloud Messaging (FCM)** - Notificaciones push
- **App Check** - Protección contra spam y abuso
- **Crashlytics** - Monitoreo de errores

### Bibliotecas Principales
- **Glide** - Carga de imágenes
- **Google Maps & Places** - Ubicación y mapas
- **Material Design 3** - Componentes de UI
- **Coroutines** - Operaciones asíncronas
- **EncryptedSharedPreferences** - Almacenamiento seguro
- **Shimmer** - Skeleton screens
- **Lottie** - Animaciones

---

## 🚀 Mejoras Recientes (v3.0.6)

### ✅ 9 Optimizaciones Implementadas (47%)

1. **ImageUtils** - Compresión automática (-90% tamaño)
2. **ValidationUtils** - Validaciones centralizadas
3. **MessageUtils** - Snackbars consistentes
4. **NetworkUtils** - Monitoreo de red en tiempo real
5. **PreferencesManager** - Preferencias encriptadas
6. **CoroutineUtils** - Helpers asíncronos
7. **AdaptadorOptimizado** - DiffUtil para RecyclerView
8. **Paginación Real** - 30 inicial, 20 incremental (antes 120 de golpe)
9. **Firebase Crashlytics** - Monitoreo automático de crashes

### 📊 Resultados Medibles

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| Tamaño imágenes | ~8 MB | ~800 KB | **-90%** |
| Items carga inicial | 120 | 30 | **-75%** |
| Memoria inicial | ~180 MB | ~60 MB | **-67%** |
| Tiempo carga | ~3s | ~1s | **-67%** |
| Datos iniciales | ~2 MB | ~500 KB | **-75%** |

---

## 📋 Roadmap

Ver [ROADMAP.md](ROADMAP.md) para la lista completa de mejoras planeadas.

### 🎯 Próximas Mejoras
- Room Database (modo offline)
- Migración gradual a MVVM
- Tests automatizados
- CI/CD Pipeline
- Analytics avanzados

---

## 🏗️ Estructura del Proyecto

```
app/src/main/java/com/cabgon/mercadeocomitan/
├── Adaptadores/          # RecyclerView adapters
├── Anuncios/            # Crear y editar anuncios
├── Chat/                # Sistema de mensajería
├── DetalleAnuncio/      # Vista detallada del producto
├── Fragmentos/          # Fragments principales (Inicio, Chats, Cuenta)
├── Modelo/              # Data classes
├── Pedidos/             # Sistema de órdenes
├── utils/               # Utilidades (Image, Validation, Network, etc.)
├── MainActivity.kt      # Activity principal
└── MainApplication.kt   # Inicialización de Firebase
```

---

## ⚙️ Configuración del Proyecto

### Prerequisitos
- Android Studio Hedgehog o superior
- JDK 17
- Cuenta de Firebase configurada

### Instalación

1. **Clonar el repositorio**
```bash
git clone https://github.com/CabgonDev/mercadeo-comitan.git
cd mercadeo-comitan
```

2. **Configurar Firebase**
   - Descarga `google-services.json` de tu proyecto Firebase
   - Colócalo en `app/google-services.json`

3. **Abrir en Android Studio**
   - File → Open → Seleccionar carpeta del proyecto
   - Esperar a que Gradle sincronice

4. **Ejecutar**
   - Conectar dispositivo o iniciar emulador
   - Run → Run 'app'

---

## 🔐 Seguridad

- ✅ Firebase App Check con Play Integrity habilitado
- ✅ Validación de datos en cliente y servidor
- ✅ EncryptedSharedPreferences para datos sensibles
- ✅ Sanitización de inputs
- ✅ Autenticación de Firebase

---

## 📄 Licencia

Este proyecto es privado y propiedad de Christian Cabrera.

---

## 👤 Autor

**Christian Cabrera**  
📧 ccabrera.e92@gmail.com  
🔗 [GitHub](https://github.com/CabgonDev)

---

## 🤝 Contribuciones

Este es un proyecto personal. No se aceptan contribuciones externas en este momento.

---

## 📝 Notas de Desarrollo

- **Idioma del código:** Español (variables, funciones, comentarios)
- **Convenciones:** camelCase para variables/funciones, PascalCase para clases
- **Documentación adicional:** Ver carpeta `docs/` y archivos en `MC Codex/`

---

## 🔗 Enlaces Útiles

- [Política de Privacidad](politica_de_privacidad_mercadeo_comitan_html.html)
- [Política de Eliminación de Cuenta](eliminacion-cuenta.html)
- [Roadmap Detallado](ROADMAP.md)

---

**Última actualización:** 2024-11-10  
**Build:** 306 (3.0.6)
