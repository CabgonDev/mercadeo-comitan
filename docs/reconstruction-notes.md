# Reconstrucción técnica

## Decisiones de producto

Mercadeo Comitán conserva su propósito local: descubrir, publicar y conversar sobre productos y servicios cercanos. La nueva experiencia usa patrones de marketplace reconocibles (búsqueda visible, categorías, grilla, favoritos, mensajes y publicación) con una identidad propia basada en amber, navy, teal y blue.

## Cambios entregados

- Splash que respeta una sesión Firebase existente.
- Shell principal con tabs persistentes para no perder scroll, filtros ni conversaciones al navegar.
- Catálogo con búsqueda, categorías, fallback explícito a Comitán cuando la ubicación no está disponible y actualización RTDB.
- Filtro de catálogo estable durante altas, cambios y eliminaciones en tiempo real.
- Favoritos cargados de forma determinista, sin temporizador fijo que oculte respuestas tardías.
- Chat con selección de imágenes del sistema, validación de tamaño, subida a Storage y mensaje RTDB tras obtener la URL.
- Publicación/edición con validación de UID, clave de anuncio e imágenes pendientes.
- Historial de pedidos escrito con claves válidas de Realtime Database y validación del participante.

## Verificación local

El proyecto mantiene un único módulo Android con ViewBinding/XML para limitar el riesgo de migración. La configuración comprobada usa Java 17 para el código, Gradle 9.6, AGP 9.4.1, Kotlin 2.2.21 y compile/target SDK 37. La compilación y las pruebas unitarias deben ejecutarse antes de cada entrega:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

La auditoría detectó advertencias de lint heredadas y carencias de reglas/backend que requieren una fase de infraestructura separada. No se despliegan reglas ni se escriben datos de producción desde este cambio.
