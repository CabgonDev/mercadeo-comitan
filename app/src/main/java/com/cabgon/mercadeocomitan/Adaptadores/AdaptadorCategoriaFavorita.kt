package com.cabgon.mercadeocomitan.Adaptadores

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cabgon.mercadeocomitan.CategoriasFavoritasActivity
import com.cabgon.mercadeocomitan.Modelo.ModeloCategoria
import com.cabgon.mercadeocomitan.databinding.ItemCategoriaFavoritaBinding

class AdaptadorCategoriaFavorita(
    private val context: Context,
    private val categoriasList: ArrayList<ModeloCategoria>
) : RecyclerView.Adapter<AdaptadorCategoriaFavorita.HolderCategoriaFavorita>() {

    private lateinit var binding: ItemCategoriaFavoritaBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderCategoriaFavorita {
        binding = ItemCategoriaFavoritaBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )
        return HolderCategoriaFavorita(binding)
    }

    override fun onBindViewHolder(holder: HolderCategoriaFavorita, position: Int) {
        val modelo = categoriasList[position]
        holder.bind(modelo)
    }

    override fun getItemCount(): Int = categoriasList.size

    inner class HolderCategoriaFavorita(private val binding: ItemCategoriaFavoritaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(modelo: ModeloCategoria) {
            // Mostrar nombre de categoría
            binding.tvCategoria.text = modelo.categoria

            // Configurar checkbox
            binding.checkboxFavorita.isChecked = modelo.esFavorita

            // Listener del checkbox
            binding.checkboxFavorita.setOnCheckedChangeListener { _, isChecked ->
                modelo.esFavorita = isChecked

                // Notificar a la Activity para actualizar contador
                if (context is CategoriasFavoritasActivity) {
                    context.onCategoriaToggled()
                }
            }

            // Hacer que todo el item sea clickeable
            binding.root.setOnClickListener {
                binding.checkboxFavorita.isChecked = !binding.checkboxFavorita.isChecked
            }
        }
    }
}
