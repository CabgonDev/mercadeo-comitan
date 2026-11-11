@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloImagenSeleccionada
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemImagenesSeleccionadasBinding
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class AdaptadorImagenSeleccionada(
    private val context : Context,
    private val imagenesSelecArrayList : ArrayList<ModeloImagenSeleccionada>,
    private val idAnuncio : String,
    private val onChanged: () -> Unit = {}
): RecyclerView.Adapter<AdaptadorImagenSeleccionada.HolderImagenSeleccionada>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): HolderImagenSeleccionada {
        val itemBinding = ItemImagenesSeleccionadasBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HolderImagenSeleccionada(itemBinding)
    }

    override fun getItemCount(): Int {
        return imagenesSelecArrayList.size
    }

    override fun onBindViewHolder(holder: HolderImagenSeleccionada, position: Int) {
        val modelo = imagenesSelecArrayList[position]

        if (modelo.deInternet) {
            try {
                val imagenUrl = modelo.imagenUrl
                Glide.with(context)
                    .load(imagenUrl)
                    .placeholder(R.drawable.item_imagen)
                    .into(holder.item_imagen)
            } catch (_: Exception) {
            }
        } else {
            try {
                val imagenUri = modelo.imagenUri
                Glide.with(context)
                    .load(imagenUri)
                    .placeholder(R.drawable.item_imagen)
                    .into(holder.item_imagen)
            } catch (_: Exception) {
            }
        }

        holder.btn_cerrar.setOnClickListener {
            if (modelo.deInternet) {
                // Declarar vistas del diseño
                val btnSi: MaterialButton
                val btnNo: MaterialButton
                val dialog = Dialog(context)

                dialog.setContentView(R.layout.cuadro_d_eliminar_imagen)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                btnSi = dialog.findViewById(R.id.Btn_si)
                btnNo = dialog.findViewById(R.id.Btn_no)

                btnSi.setOnClickListener {
                    eliminarImgFirebase(modelo, holder)
                    dialog.dismiss()
                }
                btnNo.setOnClickListener {
                    dialog.dismiss()
                }
                dialog.setCanceledOnTouchOutside(false)
                dialog.show()
            } else {
                val currentPosition = holder.bindingAdapterPosition
                imagenesSelecArrayList.remove(modelo)
                if (currentPosition != RecyclerView.NO_POSITION) {
                    notifyItemRemoved(currentPosition)
                } else {
                    notifyDataSetChanged()
                }
                onChanged()
            }
        }
    }

    private fun eliminarImgFirebase(modelo: ModeloImagenSeleccionada, holder: HolderImagenSeleccionada) {
        val idImagen = modelo.id
        if (!Constantes.esClaveFirebaseValida(idAnuncio) || !Constantes.esClaveFirebaseValida(idImagen)) return

        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        ref.child(idAnuncio).child("Imagenes").child(idImagen)
            .removeValue()
            .addOnSuccessListener {
                try {
                    val currentPosition = holder.bindingAdapterPosition
                    imagenesSelecArrayList.remove(modelo)
                    eliminarImgStorage(modelo)
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        notifyItemRemoved(currentPosition)
                    } else {
                        notifyDataSetChanged()
                    }
                    onChanged()
                } catch (_: Exception) {
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun eliminarImgStorage(modelo: ModeloImagenSeleccionada) {
        val rutaImagen = "Anuncios/" + modelo.id

        val ref = FirebaseStorage.getInstance().getReference(rutaImagen)
        ref.delete()
            .addOnSuccessListener {
                Toast.makeText(context, "La imagen se ha eliminado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onViewRecycled(holder: HolderImagenSeleccionada) {
        runCatching {
            Glide.with(context).clear(holder.item_imagen)
        }
        super.onViewRecycled(holder)
    }

    class HolderImagenSeleccionada(binding: ItemImagenesSeleccionadasBinding) : RecyclerView.ViewHolder(binding.root) {
        val item_imagen = binding.itemImagen
        val btn_cerrar = binding.cerrarItem
    }
}
