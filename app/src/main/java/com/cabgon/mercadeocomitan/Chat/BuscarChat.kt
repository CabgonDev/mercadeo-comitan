package com.cabgon.mercadeocomitan.Chat

import android.widget.Filter
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorChats
import com.cabgon.mercadeocomitan.Modelo.ModeloChats
import java.util.*

class BuscarChat(
    private val adaptador: AdaptadorChats,
    private val listaOriginal: ArrayList<ModeloChats>
) : Filter() {

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val resultados = FilterResults()

        if (constraint != null && constraint.isNotEmpty()) {
            val filtro = constraint.toString().lowercase(Locale.getDefault()).trim()
            val listaFiltrada = ArrayList<ModeloChats>()

            for (chat in listaOriginal) {
                val nombre = chat.nombres.lowercase(Locale.getDefault())
                val mensaje = chat.mensaje.lowercase(Locale.getDefault())

                if (nombre.contains(filtro) || mensaje.contains(filtro)) {
                    listaFiltrada.add(chat)
                }
            }

            resultados.count = listaFiltrada.size
            resultados.values = listaFiltrada
        } else {
            resultados.count = listaOriginal.size
            resultados.values = listaOriginal
        }

        return resultados
    }

    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
        // FilterResults.values es Any? y puede contener cualquier colección.
        // filterIsInstance valida cada elemento y evita el cast genérico no comprobable.
        val valores = when (val raw = results?.values) {
            is Iterable<*> -> raw.filterIsInstance<ModeloChats>().toCollection(ArrayList())
            else -> arrayListOf()
        }
        adaptador.chatsArrayList = valores
        adaptador.notifyDataSetChanged()
    }
}
