@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Chat.BuscarChat
import com.cabgon.mercadeocomitan.Chat.ChatActivity
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloChats
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemChatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AdaptadorChats(
    private val context: Context,
    var chatsArrayList: ArrayList<ModeloChats>
) : RecyclerView.Adapter<AdaptadorChats.HolderChats>(), Filterable {

    private var filtroLista: ArrayList<ModeloChats> = ArrayList(chatsArrayList)
    private var filtro: BuscarChat? = null
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val miUid: String get() = firebaseAuth.uid ?: ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderChats {
        val binding = ItemChatsBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderChats(binding)
    }

    override fun getItemCount(): Int = chatsArrayList.size

    override fun onBindViewHolder(holder: HolderChats, position: Int) {
        val modeloChats = chatsArrayList[position]
        val chatKey = modeloChats.keyChat

        // Guardamos la key actual en el holder para validar reciclado
        holder.currentChatKey = chatKey

        // Placeholders rápidos para evitar “basura” de reciclado
        holder.binding.apply {
            TvNombres.text = modeloChats.nombres.ifEmpty { context.getString(R.string.chat_usuario) }
            TvUltimoMensaje.text = "—"
            TvFecha.text = ""
            if (modeloChats.urlImagenPerfil.isNotBlank()) {
                Glide.with(IvPerfil)
                    .load(modeloChats.urlImagenPerfil)
                    .placeholder(R.drawable.img_perfil)
                    .error(R.drawable.img_perfil)
                    .centerCrop()
                    .into(IvPerfil)
            } else {
                IvPerfil.setImageResource(R.drawable.img_perfil)
            }
        }

        // FragmentChats ya entrega el último mensaje en el snapshot de cada
        // conversación. Evita una segunda lectura por tarjeta; solo consulta
        // Firebase para chats legacy sin resumen.
        if (modeloChats.idMensaje.isNotBlank()) {
            pintarUltimoMensaje(holder, modeloChats)
        } else {
            cargarUltimoMensaje(chatKey, holder, modeloChats)
        }

        // Cargar/actualizar info del otro usuario (si ya tienes uidRecibimos, úsalo)
        if (modeloChats.uidRecibimos.isNotEmpty()) {
            cargarInfoUsuario(modeloChats.uidRecibimos, holder, modeloChats)
        }

        // Click → abrir ChatActivity
        holder.itemView.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java).apply {
                // Pasa ambos si te sirven
                putExtra("keyChat", chatKey)
                putExtra("uidVendedor", modeloChats.uidRecibimos)
            }
            context.startActivity(intent)
        }
    }

    // ==== Cargar último mensaje del chat (maneja vacíos, reciclo, y calcula uidRecibimos) ====
    private fun cargarUltimoMensaje(chatKey: String, holder: HolderChats, modelo: ModeloChats) {
        if (!Constantes.esClaveFirebaseValida(chatKey)) return
        val ref = FirebaseDatabase.getInstance()
            .getReference("Chats")
            .child(chatKey)
            .child("mensajes")

        // orderByChild por tiempo por si tus IDs no están en orden
        ref.orderByChild("tiempo").limitToLast(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // El holder aún corresponde a este chat?
                    if (holder.currentChatKey != chatKey) return

                    if (snapshot.children.count() == 0) {
                        // Chat sin mensajes aún
                        modelo.mensaje = ""
                        modelo.tipoMensaje = ""
                        modelo.tiempo = 0L
                        holder.binding.TvUltimoMensaje.setText(R.string.chat_sin_mensajes)
                        holder.binding.TvFecha.text = ""
                        // uidRecibimos: si el modelo trae el otro uid, respétalo. Si no, no lo podemos inferir aquí.
                        return
                    }

                    val ds = snapshot.children.first()

                    val emisorUid = ds.child("emisorUid").getValue(String::class.java).orEmpty()
                    val receptorUid = ds.child("receptorUid").getValue(String::class.java).orEmpty()
                    val tipoMensaje = ds.child("tipoMensaje").getValue(String::class.java).orEmpty()
                    val mensaje = ds.child("mensaje").getValue(String::class.java).orEmpty()
                    val tiempo = ds.child("tiempo").getValue(Long::class.java) ?: 0L

                    // Ignora registros que no pertenecen al usuario autenticado.
                    // Las reglas remotas siguen siendo la autoridad; esto evita
                    // pintar datos inconsistentes si llega un snapshot legacy.
                    if (miUid != emisorUid && miUid != receptorUid) return

                    modelo.emisorUid = emisorUid
                    modelo.receptorUid = receptorUid
                    modelo.tipoMensaje = tipoMensaje
                    modelo.mensaje = mensaje
                    modelo.tiempo = tiempo

                    // Decide a quién le hablamos (el "otro")
                    modelo.uidRecibimos = if (miUid == emisorUid) receptorUid else emisorUid

                    // Pintar UI (verifica reciclado)
                    if (holder.currentChatKey != chatKey) return

                    pintarUltimoMensaje(holder, modelo)

                    // Si aún no teníamos cargado el usuario, cárgalo ahora
                    if (modelo.uidRecibimos.isNotEmpty()) {
                        cargarInfoUsuario(modelo.uidRecibimos, holder, modelo)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // No crashear UI si hay error
                }
            })
    }

    private fun pintarUltimoMensaje(holder: HolderChats, modelo: ModeloChats) {
        holder.binding.TvFecha.text = if (modelo.tiempo > 0) {
            Constantes.obtenerFechaHora(modelo.tiempo)
        } else {
            ""
        }
        holder.binding.TvUltimoMensaje.text = when (modelo.tipoMensaje) {
            Constantes.MENSAJE_TIPO_IMAGEN -> context.getString(R.string.chat_imagen_enviada)
            Constantes.MENSAJE_TIPO_TEXTO, "" -> modelo.mensaje.ifEmpty {
                context.getString(R.string.chat_sin_mensajes)
            }
            else -> modelo.mensaje
        }
    }

    // ==== Cargar información del otro usuario (nombre + avatar) ====
    private fun cargarInfoUsuario(uidOtro: String, holder: HolderChats, modelo: ModeloChats) {
        if (uidOtro.isEmpty() || !Constantes.esClaveFirebaseValida(uidOtro)) return

        val chatKeyDeEsteHolder = holder.currentChatKey

        val ref = FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uidOtro)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Evita pintar si el holder ya fue reciclado a otro chat
                if (holder.currentChatKey != chatKeyDeEsteHolder ||
                    holder.bindingAdapterPosition == RecyclerView.NO_POSITION
                ) return

                val nombres = snapshot.child("nombres").getValue(String::class.java).orEmpty()
                val imagen = snapshot.child("urlImagenPerfil").getValue(String::class.java).orEmpty()

                modelo.nombres = if (nombres.isNotBlank()) nombres else context.getString(R.string.chat_usuario)
                modelo.urlImagenPerfil = imagen

                holder.binding.TvNombres.text = modelo.nombres
                if (imagen.isNotBlank()) {
                    Glide.with(holder.binding.IvPerfil)
                        .load(imagen)
                        .placeholder(R.drawable.img_perfil)
                        .error(R.drawable.img_perfil)
                        .centerCrop()
                        .into(holder.binding.IvPerfil)
                } else {
                    holder.binding.IvPerfil.setImageResource(R.drawable.img_perfil)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onViewRecycled(holder: HolderChats) {
        holder.currentChatKey = ""
        runCatching {
            Glide.with(holder.binding.IvPerfil).clear(holder.binding.IvPerfil)
        }
        super.onViewRecycled(holder)
    }

    class HolderChats(val binding: ItemChatsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var currentChatKey: String = ""
    }

    // ====== Filtro ======
    override fun getFilter(): Filter {
        if (filtro == null) filtro = BuscarChat(this, filtroLista)
        return filtro!!
    }

    // Si actualizas la lista desde fuera, sincroniza también el respaldo del filtro
    fun submitList(nueva: ArrayList<ModeloChats>) {
        val anterior = ArrayList(chatsArrayList)
        chatsArrayList = nueva
        filtroLista = ArrayList(nueva)
        // El filtro conserva la lista que recibió al crearse. Al actualizar la
        // fuente, recréalo para no buscar sobre una instantánea vieja.
        filtro = null
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = anterior.size
            override fun getNewListSize(): Int = nueva.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                anterior[oldItemPosition].keyChat == nueva[newItemPosition].keyChat

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = anterior[oldItemPosition]
                val new = nueva[newItemPosition]
                return old.keyChat == new.keyChat &&
                    old.uidRecibimos == new.uidRecibimos &&
                    old.nombres == new.nombres &&
                    old.mensaje == new.mensaje &&
                    old.tipoMensaje == new.tipoMensaje &&
                    old.tiempo == new.tiempo
            }
        }).dispatchUpdatesTo(this)
    }
}
