package com.cabgon.mercadeocomitan.Adaptadores

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.Modelo.ModeloimgSlider
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemImagenSliderBinding
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class AdaptadorImgSlider(
    private val context: Context,
    private val imagenArrayList: ArrayList<ModeloimgSlider>
) : RecyclerView.Adapter<AdaptadorImgSlider.HolderImagenSlider>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderImagenSlider {
        val itemBinding = ItemImagenSliderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HolderImagenSlider(itemBinding)
    }

    override fun getItemCount(): Int {
        return imagenArrayList.size
    }

    override fun onBindViewHolder(holder: HolderImagenSlider, position: Int) {
        val modeloImagenSlider = imagenArrayList[position]

        val imagenUrl = modeloImagenSlider.imagenUrl
        val imagenContador = "${position + 1}/${imagenArrayList.size}"

        holder.imagenContadorTv.text = imagenContador

        try {
            Glide.with(holder.imagenIv)
                .load(imagenUrl)
                .placeholder(R.drawable.ic_imagen)
                .error(R.drawable.ic_imagen)
                .into(holder.imagenIv)
        } catch (_: Exception) {
        }

        holder.itemView.setOnClickListener {
            if (!imagenUrl.isNullOrBlank()) {
                visualizadorImagen(imagenUrl)
            }
        }
    }

    private fun visualizadorImagen(imagen: String) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.cuadro_d_visualizador_img)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val pv: PhotoView = dialog.findViewById(R.id.Pv_img)
        val btnCerrar: MaterialButton = dialog.findViewById(R.id.Btn_cerrar_visualizador)

        try {
            Glide.with(pv)
                .load(imagen)
                .placeholder(R.drawable.imagen_chat)
                .error(R.drawable.imagen_chat_falla)
                .into(pv)
        } catch (_: Exception) {
        }
        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    override fun onViewRecycled(holder: HolderImagenSlider) {
        runCatching {
            Glide.with(holder.imagenIv).clear(holder.imagenIv)
        }
        super.onViewRecycled(holder)
    }

    class HolderImagenSlider(binding: ItemImagenSliderBinding) : RecyclerView.ViewHolder(binding.root) {
        val imagenIv: ShapeableImageView = binding.ImagenIv
        val imagenContadorTv: TextView = binding.imagenContadorTv
    }
}