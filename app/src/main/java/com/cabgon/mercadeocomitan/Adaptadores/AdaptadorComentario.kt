@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloComentario
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemComentarioBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdaptadorComentario(
    private val context: Context,
    private val comentarioArrayList: ArrayList<ModeloComentario>,
) : RecyclerView.Adapter<AdaptadorComentario.HolderComentario>() {

    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderComentario {
        val binding = ItemComentarioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HolderComentario(binding)
    }

    override fun getItemCount(): Int = comentarioArrayList.size

    override fun onBindViewHolder(holder: HolderComentario, position: Int) {
        val modelo = comentarioArrayList[position]
        val fecha = modelo.tiempo.toLongOrNull()

        holder.comentarioIdVinculado = modelo.id
        holder.IvImagen.setImageResource(R.drawable.img_perfil)
        holder.TvFecha.text = fecha
            ?.takeIf { it > 0L }
            ?.let { Constantes.obtenerFecha(it) }
            ?: context.getString(R.string.comentario_fecha_desconocida)
        holder.TvComentario.text = modelo.comentario.trim()
        holder.TvNombre.text = context.getString(R.string.comentarios_usuario_generico)

        val puedeEliminar = firebaseAuth.uid == modelo.uid
        holder.itemView.isClickable = puedeEliminar
        holder.itemView.setOnClickListener(
            if (puedeEliminar) {
                { dialogEliminarComentario(modelo) }
            } else {
                null
            },
        )

        cargarInformacion(modelo, holder)
    }

    private fun dialogEliminarComentario(modelo: ModeloComentario) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.comentarios_eliminar_titulo)
            .setMessage(R.string.comentarios_eliminar_mensaje)
            .setNegativeButton(R.string.comentarios_cancelar, null)
            .setPositiveButton(R.string.comentarios_eliminar) { _, _ ->
                val uidUsuario = firebaseAuth.uid
                if (uidUsuario != modelo.uid) return@setPositiveButton
                if (!Constantes.esClaveFirebaseValida(modelo.uid_vendedor) ||
                    !Constantes.esClaveFirebaseValida(modelo.id)
                ) return@setPositiveButton

                FirebaseDatabase.getInstance()
                    .getReference("ComentariosVendedores")
                    .child(modelo.uid_vendedor)
                    .child("Comentarios")
                    .child(modelo.id)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, R.string.comentarios_eliminado, Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.comentarios_error_eliminar, error.message.orEmpty()),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
            .show()
    }

    private fun cargarInformacion(modelo: ModeloComentario, holder: HolderComentario) {
        val uid = modelo.uid
        val comentarioId = modelo.id
        if (uid.isBlank() || !Constantes.esClaveFirebaseValida(uid)) {
            holder.IvImagen.setImageResource(R.drawable.img_perfil)
            return
        }

        FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (holder.comentarioIdVinculado != comentarioId ||
                        holder.bindingAdapterPosition == RecyclerView.NO_POSITION
                    ) return
                    val nombres = snapshot.child("nombres").getValue(String::class.java).orEmpty()
                    val imagen = snapshot.child("urlImagenPerfil").getValue(String::class.java).orEmpty()
                    holder.TvNombre.text = nombres.ifBlank {
                        context.getString(R.string.comentarios_usuario_generico)
                    }

                    if (imagen.isBlank()) {
                        holder.IvImagen.setImageResource(R.drawable.img_perfil)
                    } else {
                        Glide.with(holder.itemView)
                            .load(imagen)
                            .placeholder(R.drawable.img_perfil)
                            .error(R.drawable.img_perfil)
                            .into(holder.IvImagen)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e(
                        "AdaptadorComentario",
                        "No se pudo cargar el perfil",
                        error.toException(),
                    )
                }
            })
    }

    override fun onViewRecycled(holder: HolderComentario) {
        holder.comentarioIdVinculado = null
        Glide.with(holder.itemView).clear(holder.IvImagen)
        super.onViewRecycled(holder)
    }

    class HolderComentario(
        binding: ItemComentarioBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        var comentarioIdVinculado: String? = null
        val IvImagen = binding.IbImagenPerfil
        val TvNombre = binding.TvNombresComentario
        val TvFecha = binding.TvFechaComentario
        val TvComentario = binding.TvComentario
    }
}
