@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Filtro

import android.widget.Filter
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorAnuncio
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import java.util.Locale

class FiltrarAnuncio(
    private val adaptador: AdaptadorAnuncio,
    private val filtroLista: ArrayList<ModeloAnuncio>,
) : Filter() {
    override fun performFiltering(filtro: CharSequence?): FilterResults {
        val texto = filtro?.toString()?.trim().orEmpty()
        val resultado = FilterResults()
        if (texto.isNotEmpty()){
            val consulta = texto.lowercase(Locale.ROOT)
            val filtroModelo = ArrayList<ModeloAnuncio>()
            for (i in filtroLista.indices){
                if (filtroLista[i].titulo.lowercase(Locale.ROOT).contains(consulta) ||
                    filtroLista[i].marca.lowercase(Locale.ROOT).contains(consulta) ||
                    filtroLista[i].condicion.lowercase(Locale.ROOT).contains(consulta) ||
                    filtroLista[i].categoria.lowercase(Locale.ROOT).contains(consulta)){
                    filtroModelo.add(filtroLista[i])
                }
            }
            resultado.count = filtroModelo.size
            resultado.values = filtroModelo
        }else{
            resultado.count = filtroLista.size
            resultado.values = filtroLista
        }
        return resultado
    }

    override fun publishResults(filtro: CharSequence?, resultado: FilterResults) {
        // FilterResults.values es Any?; comprobar la colección y sus elementos
        // evita un cast genérico no comprobable en la compilación release.
        val valores = when (val raw = resultado.values) {
            is Iterable<*> -> raw.filterIsInstance<ModeloAnuncio>()
            else -> emptyList()
        }
        adaptador.anuncioArrayList = ArrayList(valores)
        adaptador.notifyDataSetChanged()
        adaptador.onResultsChanged?.invoke(valores.size)
    }

}
