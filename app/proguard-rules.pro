# --- Solo lo necesario para Firebase (scope fino) ---

# Mantén completas SOLO las clases de tu paquete de modelos que estén anotadas con @Keep.
# Esto evita reglas amplias y reduce el alcance a lo estrictamente usado por reflection.
-keep @androidx.annotation.Keep class com.cabgon.mercadeocomitan.Modelo.** { *; }

# Si por cualquier razón hay modelos sin @Keep pero con IgnoreExtraProperties, puedes usar esta alternativa (una u otra, no ambas):
# -keep @com.google.firebase.database.IgnoreExtraProperties class com.cabgon.mercadeocomitan.Modelo.** { *; }

# Asegura constructores y miembros de modelos (por si ofuscas nombres de clase pero quieres que los campos sigan mapeando)
-keepclasseswithmembers class com.cabgon.mercadeocomitan.Modelo.** { <init>(...); }
-keepclassmembers class com.cabgon.mercadeocomitan.Modelo.** { *; }

# Conserva anotaciones (necesario para @Keep / @IgnoreExtraProperties)
-keepattributes *Annotation*

# --- Evita reglas amplias (ELIMINADAS) ---
# (Quitamos estas porque son la causa del warning y no son necesarias)
# -keep class com.cabgon.mercadeocomitan.Modelo.** { *; }
# -keep class com.google.firebase.** { *; }

# Warnings del SDK de Firebase (no rompe; limpia ruido en el build)
-dontwarn com.google.firebase.**

# Mantener tu servicio FCM
-keep class com.cabgon.mercadeocomitan.notif.AppMessagingService { *; }
-keep class com.cabgon.mercadeocomitan.Chat.MyFirebaseMessagingService { *; }
-keepclassmembers class * extends com.google.firebase.messaging.FirebaseMessagingService { *; }

# --- Google Maps ---
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.maps.**

# Mantener callbacks de Maps
-keepclassmembers class * implements com.google.android.gms.maps.OnMapReadyCallback {
    public void onMapReady(com.google.android.gms.maps.GoogleMap);
}

