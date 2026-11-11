@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloChat
import com.cabgon.mercadeocomitan.R
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AdaptadorChat(
    private val context: Context,
    private val chatArray: ArrayList<ModeloChat>,
    private val chatId: String // 👈 usa la MISMA ruta que lees/envías en ChatActivity
) : RecyclerView.Adapter<AdaptadorChat.HolderChat>() {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    companion object {
        private const val MENSAJE_IZQUIERDO = 0
        private const val MENSAJE_DERECHO = 1
    }

    override fun getItemCount(): Int = chatArray.size

    override fun getItemViewType(position: Int): Int =
        if (chatArray[position].emisorUid == firebaseAuth.uid) MENSAJE_DERECHO else MENSAJE_IZQUIERDO

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderChat {
        val layout = if (viewType == MENSAJE_DERECHO) R.layout.item_chat_derecho else R.layout.item_chat_izquierdo
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        return HolderChat(view)
    }

    override fun onBindViewHolder(holder: HolderChat, position: Int) {
        val modelo = chatArray[position]
        val tipo = modelo.tipoMensaje
        val hora = runCatching { Constantes.obtenerFechaHora(modelo.tiempo) }.getOrDefault("")
        holder.tvHora.text = hora

        // Reseteo de vistas por reciclado
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.tvMensaje.visibility = View.GONE
        holder.ivFoto.visibility = View.GONE

        if (tipo == Constantes.MENSAJE_TIPO_TEXTO) {
            // ---- TEXTO ----
            holder.tvMensaje.visibility = View.VISIBLE
            holder.tvMensaje.text = modelo.mensaje

            if (modelo.emisorUid == firebaseAuth.uid) {
                // Acciones por long click (eliminar)
                holder.itemView.setOnLongClickListener {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("¿Qué desea realizar?")
                        .setItems(arrayOf("Eliminar mensaje", "Cancelar")) { _, i ->
                            if (i == 0) eliminarMensajeSafe(holder.bindingAdapterPosition, modelo)
                        }
                        .show()
                    true
                }
            }

        } else {
            // ---- IMAGEN ----
            holder.ivFoto.visibility = View.VISIBLE
            val imageUrl = modelo.mensaje.takeIf { esUrlImagenSegura(it) }
            Glide.with(holder.ivFoto)
                .load(imageUrl)
                .apply(RequestOptions().placeholder(R.drawable.imagen_chat).error(R.drawable.imagen_chat_falla))
                .into(holder.ivFoto)

            // Click abre visor
            if (imageUrl != null) {
                holder.itemView.setOnClickListener { visualizadorImagen(imageUrl) }
            }

            // Long click para eliminar si es tuya
            if (modelo.emisorUid == firebaseAuth.uid) {
                holder.itemView.setOnLongClickListener {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("¿Qué desea realizar?")
                        .setItems(arrayOf("Eliminar imagen", "Cancelar")) { _, i ->
                            if (i == 0) eliminarMensajeSafe(holder.bindingAdapterPosition, modelo)
                        }
                        .show()
                    true
                }
            }
        }

        // ---- Palomitas (solo para tus mensajes) ----
// Palomitas (solo mis mensajes)
        if (getItemViewType(position) == MENSAJE_DERECHO) {
            holder.ivEstado?.visibility = View.VISIBLE
            val estado = modelo.estado.trim().lowercase()
            when (estado) {
                "leido" -> {
                    holder.ivEstado?.setImageResource(R.drawable.ic_chat_doble_check_read)
                    holder.ivEstado?.imageTintList = null
                    holder.ivEstado?.imageTintMode = null
                }
                "entregado" -> {
                    holder.ivEstado?.setImageResource(R.drawable.ic_chat_doble_check)
                    holder.ivEstado?.imageTintList = null // si tu doble_check gris ya viene pintado en el vector, deja null
                }
                else -> {
                    holder.ivEstado?.setImageResource(R.drawable.ic_chat_check)
                    holder.ivEstado?.imageTintList = null
                }
            }
        } else {
            holder.ivEstado?.visibility = View.GONE
        }


    }

    override fun onViewRecycled(holder: HolderChat) {
        super.onViewRecycled(holder)
        // Limpia Glide para evitar imágenes cruzadas
        Glide.with(holder.ivFoto).clear(holder.ivFoto)
    }

    private fun eliminarMensajeSafe(position: Int, modeloChat: ModeloChat) {
        val pos = if (position in 0 until chatArray.size) position else return
        if (chatArray[pos].idMensaje != modeloChat.idMensaje) return
        val msgId = modeloChat.idMensaje
        if (!Constantes.esClaveFirebaseValida(chatId) || !Constantes.esClaveFirebaseValida(msgId)) return
        FirebaseDatabase.getInstance().reference
            .child("Chats").child(chatId).child("mensajes").child(msgId)
            .removeValue()
            .addOnSuccessListener { Toast.makeText(context, "Mensaje eliminado", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(context, e.message ?: "Error al eliminar", Toast.LENGTH_SHORT).show() }
    }

    private fun visualizadorImagen(imagen: String) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.cuadro_d_visualizador_img)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val pv: PhotoView = dialog.findViewById(R.id.Pv_img)
        val btnCerrar: MaterialButton = dialog.findViewById(R.id.Btn_cerrar_visualizador)
        Glide.with(pv).load(imagen)
            .placeholder(R.drawable.imagen_chat)
            .error(R.drawable.imagen_chat_falla)
            .into(pv)
        btnCerrar.setOnClickListener { dialog.dismiss() }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun esUrlImagenSegura(url: String): Boolean {
        val uri = runCatching { url.toUri() }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank()
    }

    class HolderChat(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMensaje: TextView = itemView.findViewById(R.id.tvMensaje)
        val ivFoto: ShapeableImageView = itemView.findViewById(R.id.ivFoto)
        val tvHora: TextView = itemView.findViewById(R.id.tvHora)
        val ivEstado: ImageView? = itemView.findViewById(R.id.ivEstado) // solo en el derecho
    }
}
