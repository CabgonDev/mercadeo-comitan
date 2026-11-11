@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.cabgon.mercadeocomitan.Modelo.ModeloCategoria
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.RvListenerCategoria
import com.cabgon.mercadeocomitan.databinding.ItemCategoriaInicioBinding

class AdaptadorCategoria(
    private val context : Context,
    private val categoriaArrayList : ArrayList<ModeloCategoria>,
    private val rvListenerCategoria : RvListenerCategoria
): Adapter<AdaptadorCategoria.holderCategoria>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): holderCategoria {
        val itemBinding = ItemCategoriaInicioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return holderCategoria(itemBinding)
    }

    override fun getItemCount(): Int {
        return categoriaArrayList.size
    }

    override fun onBindViewHolder(holder: holderCategoria, position: Int) {
        val modeloCategoria = categoriaArrayList[position]

        val icono = modeloCategoria.icono
        val categoria = modeloCategoria.categoria

        holder.categoriaIconoIv.setImageResource(icono)
        holder.categoriaTv.text = categoria
        val colores = intArrayOf(
            R.color.md_theme_primaryContainer,
            R.color.warning_color,
            R.color.azul_claro,
            R.color.chat_bubble_in,
        )
        holder.categoriaIconoIv.setBackgroundColor(
            ContextCompat.getColor(context, colores[position % colores.size])
        )

        holder.itemView.setOnClickListener {
            rvListenerCategoria.onCategoriaClick(modeloCategoria)
        }

    }


    class holderCategoria(binding: ItemCategoriaInicioBinding) : ViewHolder(binding.root) {
        val categoriaIconoIv = binding.categoriaIconoIv
        val categoriaTv = binding.TvCategoria
    }
}
