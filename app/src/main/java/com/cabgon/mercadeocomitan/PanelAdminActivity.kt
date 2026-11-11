package com.cabgon.mercadeocomitan

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cabgon.mercadeocomitan.databinding.ActivityPanelAdminBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class PanelAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPanelAdminBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    private var esAdmin = false
    private var esSuperAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance()

        validarPermisos()
    }

    // ============================
    // VALIDACIÓN DE PERMISOS
    // ============================
    private fun validarPermisos() {
        val uid = firebaseAuth.uid ?: return finish()
        db.getReference("Usuarios").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val rol = snap.child("rol").getValue(String::class.java) ?: ""
                    esSuperAdmin = rol == Constantes.ROL_SUPERADMIN
                    esAdmin = rol == Constantes.ROL_ADMIN

                    if (!esSuperAdmin && !esAdmin) {
                        Toast.makeText(
                            this@PanelAdminActivity,
                            "No tienes permisos de administrador",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return
                    }

                    initPanel()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@PanelAdminActivity,
                        "Error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            })
    }

    // ============================
    // INICIALIZAR PANEL
    // ============================
    private fun initPanel() {
        // ====== SECCIÓN MENSAJE GLOBAL ======
        if (esSuperAdmin) setupMensajeGlobal()
        else ocultarSeccionMensaje()

        // ====== OTROS BOTONES ======
        binding.btnBloquearUsuario.setOnClickListener {
            if (!esSuperAdmin) {
                Toast.makeText(this, "Solo el superadmin puede bloquear usuarios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialogBloquearUsuario()
        }

        binding.btnEliminarAnuncio.setOnClickListener { dialogEliminarAnuncio() }

        binding.btnPublicarGratis.setOnClickListener {
            if (!esSuperAdmin) {
                Toast.makeText(this, "Solo el superadmin puede dar publicar gratis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialogPublicarGratis()
        }

        binding.btnHacerAdmin.setOnClickListener {
            if (!esSuperAdmin) {
                Toast.makeText(this, "Solo el superadmin puede crear admins", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialogHacerAdmin()
        }
    }

    private fun ocultarSeccionMensaje() {
        binding.tvMensajeActual.visibility = View.GONE
        binding.swMensajeActivo.visibility = View.GONE
        binding.etTituloMensaje.visibility = View.GONE
        binding.etCuerpoMensaje.visibility = View.GONE
        binding.btnGuardarMensaje.visibility = View.GONE
        binding.btnDesactivarMensaje.visibility = View.GONE
        binding.btnBorrarMensaje.visibility = View.GONE
    }

    private fun setupMensajeGlobal() {
        val refMsg = db.getReference("configApp").child("mensajeGlobal")

        // cargar mensaje actual
        binding.progressMsg.visibility = View.VISIBLE
        refMsg.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                binding.progressMsg.visibility = View.GONE
                if (snap.exists()) {
                    val activo = snap.child("activo").getValue(Boolean::class.java) ?: false
                    val titulo = snap.child("titulo").getValue(String::class.java) ?: ""
                    val cuerpo = snap.child("cuerpo").getValue(String::class.java) ?: ""
                    binding.tvMensajeActual.text =
                        (if (activo) "🟢 ACTIVO\n" else "⚪ DESACTIVADO\n") + "$titulo\n\n$cuerpo"
                    binding.swMensajeActivo.isChecked = activo
                } else {
                    binding.tvMensajeActual.text = "Sin mensaje global"
                    binding.swMensajeActivo.isChecked = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressMsg.visibility = View.GONE
                Toast.makeText(this@PanelAdminActivity, "Error al cargar", Toast.LENGTH_SHORT).show()
            }
        })

        // guardar / actualizar
        binding.btnGuardarMensaje.setOnClickListener {
            val titulo = binding.etTituloMensaje.text.toString().trim()
            val cuerpo = binding.etCuerpoMensaje.text.toString().trim()
            val activo = binding.swMensajeActivo.isChecked

            if (titulo.isEmpty() || cuerpo.isEmpty()) {
                Toast.makeText(this, "Completa título y mensaje", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = mapOf(
                "activo" to activo,
                "titulo" to titulo,
                "cuerpo" to cuerpo
            )
            binding.progressMsg.visibility = View.VISIBLE
            refMsg.setValue(data).addOnSuccessListener {
                binding.progressMsg.visibility = View.GONE
                binding.tvMensajeActual.text =
                    (if (activo) "🟢 ACTIVO\n" else "⚪ DESACTIVADO\n") + "$titulo\n\n$cuerpo"
                Toast.makeText(this, "Mensaje actualizado ✅", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                binding.progressMsg.visibility = View.GONE
                Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
            }
        }

        // desactivar mensaje
        binding.btnDesactivarMensaje.setOnClickListener {
            refMsg.child("activo").setValue(false).addOnSuccessListener {
                binding.swMensajeActivo.isChecked = false
                Toast.makeText(this, "Mensaje desactivado ⚪", Toast.LENGTH_SHORT).show()
            }
        }

        // borrar mensaje global
        binding.btnBorrarMensaje.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Borrar completamente el mensaje global?")
                .setPositiveButton("Sí, borrar") { _, _ ->
                    refMsg.removeValue().addOnSuccessListener {
                        Toast.makeText(this, "Mensaje borrado 🗑️", Toast.LENGTH_SHORT).show()
                        binding.tvMensajeActual.text = "Sin mensaje global"
                        binding.etTituloMensaje.text.clear()
                        binding.etCuerpoMensaje.text.clear()
                        binding.swMensajeActivo.isChecked = false
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // =========================
    // BLOQUEAR USUARIO
    // =========================
    private fun dialogBloquearUsuario() {
        val et = EditText(this)
        et.hint = "UID o email del usuario"

        AlertDialog.Builder(this)
            .setTitle("Bloquear usuario")
            .setView(et)
            .setPositiveButton("Bloquear") { _, _ ->
                val valor = et.text.toString().trim()
                if (valor.isEmpty()) return@setPositiveButton
                bloquearUsuario(valor)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Un email contiene puntos y @; NO es una clave válida para ref.child(email).
    // Validamos y resolvemos la ruta antes de operar para evitar DatabaseException.
    private fun resolverUsuarios(entrada: String, onEncontrados: (List<DatabaseReference>) -> Unit) {
        val valor = entrada.trim()
        if (valor.isBlank()) return
        val ref = db.getReference("Usuarios")

        if (valor.contains('@')) {
            ref.orderByChild("email").equalTo(valor).get()
                .addOnSuccessListener { snapshot ->
                    val encontrados = snapshot.children.map { it.ref }
                    if (encontrados.isEmpty()) {
                        Toast.makeText(this, "No se encontró el usuario", Toast.LENGTH_SHORT).show()
                    } else {
                        onEncontrados(encontrados)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "No se pudo buscar el usuario", Toast.LENGTH_SHORT).show()
                }
            return
        }

        if (!Constantes.esClaveFirebaseValida(valor)) {
            Toast.makeText(this, "UID inválido", Toast.LENGTH_SHORT).show()
            return
        }
        ref.child(valor).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) onEncontrados(listOf(snapshot.ref))
                else Toast.makeText(this, "No se encontró el usuario", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "No se pudo buscar el usuario", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bloquearUsuario(uidOEmail: String) {
        resolverUsuarios(uidOEmail) { usuarios ->
            usuarios.forEach { usuario ->
                usuario.child("bloqueado").setValue(true)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Usuario bloqueado", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "No se pudo bloquear", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // =========================
    // ELIMINAR ANUNCIO
    // =========================
    private fun dialogEliminarAnuncio() {
        val et = EditText(this)
        et.hint = "ID del anuncio"

        AlertDialog.Builder(this)
            .setTitle("Eliminar anuncio")
            .setView(et)
            .setPositiveButton("Eliminar") { _, _ ->
                val id = et.text.toString().trim()
                if (!Constantes.esClaveFirebaseValida(id)) {
                    Toast.makeText(this, "ID de anuncio inválido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                db.getReference("Anuncios").child(id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Anuncio eliminado ✅", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================
    // PUBLICAR GRATIS
    // =========================
    private fun dialogPublicarGratis() {
        val et = EditText(this)
        et.hint = "UID o email del usuario"

        AlertDialog.Builder(this)
            .setTitle("Dar publicar gratis")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isEmpty()) return@setPositiveButton
                resolverUsuarios(v) { usuarios ->
                    usuarios.forEach { usuario ->
                        usuario.updateChildren(mapOf("puedePublicarGratis" to true, "canPost" to true))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Permiso otorgado", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "No se pudo otorgar permiso", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================
    // HACER ADMIN
    // =========================
    private fun dialogHacerAdmin() {
        val et = EditText(this)
        et.hint = "UID o email del usuario"

        AlertDialog.Builder(this)
            .setTitle("Hacer ADMIN (solo eliminar anuncios)")
            .setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isEmpty()) return@setPositiveButton
                resolverUsuarios(v) { usuarios ->
                    usuarios.forEach { usuario ->
                        usuario.updateChildren(
                            mapOf("rol" to Constantes.ROL_ADMIN, "puedePublicarGratis" to true)
                        )
                            .addOnSuccessListener {
                                Toast.makeText(this, "Administrador creado", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "No se pudo actualizar el rol", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
