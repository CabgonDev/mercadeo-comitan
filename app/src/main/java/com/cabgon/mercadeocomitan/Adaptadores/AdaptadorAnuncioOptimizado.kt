@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.DetalleAnuncio.DetalleAnuncio
import com.cabgon.mercadeocomitan.Filtro.FiltrarAnuncio
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemAnuncioNuevaVersionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdaptadorAnuncio : RecyclerView.Adapter<AdaptadorAnuncio.HolderAnuncio>, Filterable {

    private var context: Context
    var anuncioArrayList: ArrayList<ModeloAnuncio>
    private var firebaseAuth: FirebaseAuth
    private var filtroLista: ArrayList<ModeloAnuncio>
    private var filtro: FiltrarAnuncio? = null
    private val primeraImagenCache = HashMap<String, String>()

    /** La pantalla muestra el estado vacío tras aplicar búsqueda y categorías. */
    var onResultsChanged: ((Int) -> Unit)? = null

    constructor(context: Context, anuncioArrayList: ArrayList<ModeloAnuncio>) {
        this.context = context
        this.anuncioArrayList = anuncioArrayList
        firebaseAuth = FirebaseAuth.getInstance()
        this.filtroLista = anuncioArrayList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderAnuncio {
        val itemBinding = ItemAnuncioNuevaVersionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HolderAnuncio(itemBinding)
    }

    override fun getItemCount(): Int {
        return anuncioArrayList.size
    }

    override fun onBindViewHolder(holder: HolderAnuncio, position: Int) {
        val modeloAnuncio = anuncioArrayList[position]

        val titulo = modeloAnuncio.titulo
        val descripcion = modeloAnuncio.descripcion
        val direccion = modeloAnuncio.direccion
        val condicion = modeloAnuncio.condicion
        val precio = modeloAnuncio.precio
        val tiempo = modeloAnuncio.tiempo

        val formatoFecha = Constantes.obtenerFecha(tiempo)

        holder.anuncioIdVinculado = modeloAnuncio.id
        limpiarCargaImagen(holder)
        holder.imagenIv.setImageResource(R.drawable.ic_imagen)
        cargarPrimeraImgAnuncio(modeloAnuncio, holder)

        comprobarFavorito(modeloAnuncio, holder)

        holder.Tv_titulo.text = titulo
        holder.Tv_descripcion.text = descripcion
        holder.Tv_direccion.text = direccion
        holder.Tv_condicion.text = condicion
        holder.Tv_precio.text = precio
        holder.Tv_fecha.text = formatoFecha

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetalleAnuncio::class.java)
            intent.putExtra("idAnuncio", modeloAnuncio.id)
            context.startActivity(intent)
        }

        holder.Ib_fav.setOnClickListener {
            if (firebaseAuth.currentUser?.uid.isNullOrBlank()) {
                Toast.makeText(context, "Inicia sesión para guardar favoritos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val favorito = modeloAnuncio.favorito

            if (favorito) {
                modeloAnuncio.favorito = false
                holder.Ib_fav.setImageResource(R.drawable.ic_no_favorito)
                Constantes.eliminarAnuncioFav(context, modeloAnuncio.id)
            } else {
                modeloAnuncio.favorito = true
                holder.Ib_fav.setImageResource(R.drawable.ic_anuncio_es_favorito)
                Constantes.agregarAnuncioFav(context, modeloAnuncio.id)
            }
        }
    }

    private fun comprobarFavorito(modeloAnuncio: ModeloAnuncio, holder: HolderAnuncio) {
        val uid = firebaseAuth.currentUser?.uid
        val idAnuncio = modeloAnuncio.id
        if (uid == null || !Constantes.esClaveFirebaseValida(uid) || !Constantes.esClaveFirebaseValida(idAnuncio)) {
            // Usuario no logueado o clave inválida: muestra corazón vacío y no escuches Firebase
            holder.Ib_fav.setImageResource(R.drawable.ic_no_favorito)
            modeloAnuncio.favorito = false
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(uid)
            .child("Favoritos")
            .child(idAnuncio)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (holder.anuncioIdVinculado != idAnuncio ||
                    holder.bindingAdapterPosition == RecyclerView.NO_POSITION
                ) return
                val favorito = snapshot.exists()
                modeloAnuncio.favorito = favorito
                holder.Ib_fav.setImageResource(
                    if (favorito) R.drawable.ic_anuncio_es_favorito
                    else R.drawable.ic_no_favorito
                )
            }

            override fun onCancelled(error: DatabaseError) {
                // No hagas throw, solo log o ignora para evitar crash
                android.util.Log.w("AdaptadorAnuncio", "Error favoritos: ${error.message}")
            }
        })
    }

    private fun cargarPrimeraImgAnuncio(modeloAnuncio: ModeloAnuncio, holder: HolderAnuncio) {
        val idAnuncio = modeloAnuncio.id
        if (!Constantes.esClaveFirebaseValida(idAnuncio)) return

        val urlEnCache = primeraImagenCache[idAnuncio]
        if (!urlEnCache.isNullOrBlank()) {
            Glide.with(context.applicationContext)
                .load(urlEnCache)
                .placeholder(R.drawable.ic_imagen)
                .error(R.drawable.ic_imagen)
                .into(holder.imagenIv)
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        ref.child(idAnuncio).child("Imagenes").limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (holder.anuncioIdVinculado != idAnuncio ||
                        holder.bindingAdapterPosition == RecyclerView.NO_POSITION
                    ) return
                    for (ds in snapshot.children) {
                        val imagenUrl = ds.child("imagenUrl").getValue(String::class.java)
                            ?: ds.child("urlImagen").getValue(String::class.java)
                            ?: ""
                        if (imagenUrl.isBlank()) continue
                        primeraImagenCache[idAnuncio] = imagenUrl
                        Glide.with(context.applicationContext)
                            .load(imagenUrl)
                            .placeholder(R.drawable.ic_imagen)
                            .error(R.drawable.ic_imagen)
                            .into(holder.imagenIv)
                        break
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.w("AdaptadorAnuncio", "Error cargando imagen: ${error.message}")
                }
            })
    }

    override fun onViewRecycled(holder: HolderAnuncio) {
        holder.anuncioIdVinculado = null
        limpiarCargaImagen(holder)
        super.onViewRecycled(holder)
    }

    private fun limpiarCargaImagen(holder: HolderAnuncio) {
        runCatching {
            Glide.with(context.applicationContext).clear(holder.imagenIv)
        }.onFailure { error ->
            android.util.Log.w(
                "AdaptadorAnuncio",
                "No se pudo limpiar la imagen reciclada",
                error
            )
        }
    }

    /**
     * Actualiza la lista de anuncios usando DiffUtil para optimizar el rendimiento
     */
    fun actualizarListaConDiff(nuevaLista: ArrayList<ModeloAnuncio>) {
        val diffCallback = AnuncioDiffCallback(anuncioArrayList, nuevaLista)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        anuncioArrayList.clear()
        anuncioArrayList.addAll(nuevaLista)
        filtroLista = anuncioArrayList

        diffResult.dispatchUpdatesTo(this)
        onResultsChanged?.invoke(anuncioArrayList.size)
    }

    /**
     * Limpia la caché de imágenes
     */
    fun limpiarCache() {
        primeraImagenCache.clear()
    }

    class HolderAnuncio(binding: ItemAnuncioNuevaVersionBinding) : RecyclerView.ViewHolder(binding.root) {
        var anuncioIdVinculado: String? = null
        val imagenIv = binding.imagenIv
        val Tv_titulo = binding.TvTitulo
        val Tv_descripcion = binding.TvDescripcion
        val Tv_direccion = binding.TvDireccion
        val Tv_condicion = binding.TvCondicion
        val Tv_precio = binding.TvPrecio
        val Tv_fecha = binding.TvFecha
        val Ib_fav = binding.IbFav
    }

    override fun getFilter(): Filter {
        if (filtro == null) {
            filtro = FiltrarAnuncio(this, filtroLista)
        }
        return filtro as FiltrarAnuncio
    }

    /**
     * DiffUtil.Callback para optimizar actualizaciones de la lista
     */
    private class AnuncioDiffCallback(
        private val oldList: List<ModeloAnuncio>,
        private val newList: List<ModeloAnuncio>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return oldItem.id == newItem.id &&
                    oldItem.titulo == newItem.titulo &&
                    oldItem.precio == newItem.precio &&
                    oldItem.estado == newItem.estado &&
                    oldItem.favorito == newItem.favorito &&
                    oldItem.tiempo == newItem.tiempo
        }
    }
}
