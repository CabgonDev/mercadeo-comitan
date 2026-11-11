package com.cabgon.mercadeocomitan

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.core.net.toUri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.Calendar
import java.util.Locale

object Constantes {

    // --- Admin
    const val ROL_SUPERADMIN = "superadmin"
    const val ROL_ADMIN = "admin"
    const val ROL_USUARIO = "usuario"

    // --- Tipos de mensaje ---
    const val MENSAJE_TIPO_TEXTO = "texto"
    const val MENSAJE_TIPO_IMAGEN = "imagen"

    // --- Estados de anuncio ---
    const val anuncio_disponible = "Disponible"
    const val anuncio_vendido = "Vendido"

    // --- Métodos de pago locales ---
    const val PAGO_CONTRA_ENTREGA = "contra_entrega"
    const val PAGO_TRANSFERENCIA = "transferencia"

    // --- Categorías ---
    val categorias = arrayOf(
        "Todos",
        "Celulares",
        "Computadoras/Laptops",
        "Electrónica",
        "Electrodomésticos",
        "Vehículos",
        "Consolas y Videojuegos",
        "Juguetes y Figuras",
        "Hogar y Muebles",
        "Belleza y Cuidado Personal",
        "Libros y Revistas",
        "Deportes y Fitness",
        "Renta y Venta de Inmuebles",
        "Servicios",
        "Alimentos y Bebidas",
        "Ropa y Calzado",
        "Mascotas",
        "Otros",
    )

    val categoriasIcono = arrayOf(
        R.drawable.ic_categoria_todos,
        R.drawable.ic_categoria_mobiles,
        R.drawable.ic_categoria_ordenadores,
        R.drawable.ic_categoria_electronica,
        R.drawable.ic_categoria_electrodomesticos,
        R.drawable.ic_categoria_vehiculos,
        R.drawable.ic_categoria_consolas,
        R.drawable.ic_categoria_juguetes,
        R.drawable.ic_categoria_muebles,
        R.drawable.ic_categoria_belleza,
        R.drawable.ic_categoria_libros,
        R.drawable.ic_categoria_deportes,
        R.drawable.ic_categoria_renta,
        R.drawable.ic_categoria_servicios,
        R.drawable.ic_categoria_comida,
        R.drawable.ic_categoria_ropa,
        R.drawable.ic_categoria_mascotas,
        R.drawable.ic_categoria_otros,
    )

    val condiciones = arrayOf(
        "Nuevo",
        "Semi-Nuevo",
        "Usado",
        "Alimento y/o Bebida Preparad@",
        "Producto Artesanal",
        "No Aplica"
    )

    // --- Utilidades de tiempo ---
    fun obtenerTiempoDis(): Long = System.currentTimeMillis()

    fun obtenerFecha(tiempo: Long): String {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = tiempo
        return DateFormat.format("dd/MM/yyyy", cal).toString()
    }

    fun obtenerFechaHora(tiempo: Long): String {
        val cal = Calendar.getInstance(Locale.ENGLISH)
        cal.timeInMillis = tiempo
        return DateFormat.format("dd/MM/yyyy hh:mm:aa", cal).toString()
    }

    // --- Favoritos ---
    fun agregarAnuncioFav(context: Context, idAnuncio: String) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        if (!esClaveFirebaseValida(idAnuncio)) return
        val appContext = context.applicationContext
        val tiempo = obtenerTiempoDis()
        val datos = hashMapOf("idAnuncio" to idAnuncio, "tiempo" to tiempo)
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios")
        ref.child(uid).child("Favoritos").child(idAnuncio)
            .setValue(datos)
            .addOnSuccessListener {
                Toast.makeText(appContext, "Agregado a favoritos", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(appContext, "No se pudo guardar el favorito", Toast.LENGTH_SHORT).show()
            }
    }

    fun eliminarAnuncioFav(context: Context, idAnuncio: String) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        if (!esClaveFirebaseValida(idAnuncio)) return
        val appContext = context.applicationContext
        val ref = FirebaseDatabase.getInstance().getReference("Usuarios")
        ref.child(uid).child("Favoritos").child(idAnuncio)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(appContext, "Eliminado de favoritos", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(appContext, "No se pudo quitar el favorito", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Mapa ---
    fun mapaIntent(context: Context, latitud: Double, longitud: Double) {
        val uri = "https://maps.google.com/maps?daddr=$latitud,$longitud".toUri()
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No se encontró una aplicación de mapas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Llamada ---
    fun llamarIntent(context: Context, tel: String) {
        // ACTION_DIAL deja la confirmación final al usuario y no requiere
        // CALL_PHONE, evitando un fallo si el permiso aún no fue concedido.
        val numeroLimpio = tel.trim()
        if (numeroLimpio.isEmpty()) {
            Toast.makeText(context, "El vendedor aún no tiene número telefónico", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, "tel:$numeroLimpio".toUri())
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No se pudo abrir la aplicación de teléfono", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Ruta del chat (determinística, evita duplicados) ---
    fun rutaChat(uid1: String, uid2: String): String {
        val a = uid1.trim()
        val b = uid2.trim()
        require(a.isNotEmpty() && b.isNotEmpty()) { "UID vacío en rutaChat()" }
        return if (a <= b) "${a}_$b" else "${b}_$a"
    }

    // Los IDs que provienen de Intent/UI no deben convertirse en rutas arbitrarias.
    fun esClaveFirebaseValida(valor: String): Boolean =
        valor.isNotBlank() && valor.length <= 128 &&
            valor.none { it in ".#$[]/" || it.code < 32 || it.code == 127 }

    // Incremento atómico: no pierde visitas cuando hay usuarios simultáneos.
    // Las reglas remotas deben autorizar exclusivamente este contador.
    fun incrementarVistas(idAnuncio: String) {
        if (!esClaveFirebaseValida(idAnuncio)) return
        FirebaseDatabase.getInstance().getReference("Anuncios")
            .child(idAnuncio).child("contadorVistas")
            .setValue(ServerValue.increment(1L))
    }
}
