@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Fragmentos

import android.content.Context
import android.os.Bundle
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorAnuncio
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import com.cabgon.mercadeocomitan.databinding.FragmentFavAnunciosBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicInteger


class Fav_Anuncios_Fragment : Fragment() {

    private var _binding: FragmentFavAnunciosBinding? = null
    private val binding get() = _binding!!
    private lateinit var mContext: Context
    private lateinit var firebaseAuth: FirebaseAuth

    private val anunciosArrayList = ArrayList<ModeloAnuncio>()
    private lateinit var anunciosAdaptador: AdaptadorAnuncio
    private var favoritosRef: DatabaseReference? = null
    private var favoritosListener: ValueEventListener? = null
    private var versionCarga = 0L

    override fun onAttach(context: Context) {
        this.mContext = context
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavAnunciosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
        anunciosAdaptador = AdaptadorAnuncio(mContext, anunciosArrayList).also { adapter ->
            adapter.onResultsChanged = { cantidad ->
                _binding?.tvVacio?.visibility = if (cantidad == 0) View.VISIBLE else View.GONE
            }
        }
        binding.anunciosRv.adapter = anunciosAdaptador
        cargarAnunciosFav()

        binding.EtBuscar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(filtro: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    val consulta = filtro.toString()
                    anunciosAdaptador.filter.filter(consulta)
                } catch (_: Exception) {
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.IbLimpiar.setOnClickListener {
            val consulta = binding.EtBuscar.text.toString().trim()
            if (consulta.isNotEmpty()){
                binding.EtBuscar.setText("")
                Toast.makeText(context,
                    "Se ha limpiado el campo de busqueda",
                    Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(context,
                    "No se ha ingresado una consulta",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cargarAnunciosFav() {
        val uid = firebaseAuth.uid?.takeIf { it.isNotBlank() && Constantes.esClaveFirebaseValida(it) } ?: run {
            actualizarLista(emptyList())
            return
        }
        favoritosListener?.let { favoritosRef?.removeEventListener(it) }
        favoritosRef = FirebaseDatabase.getInstance().getReference("Usuarios")
            .child(uid)
            .child("Favoritos")
        favoritosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || _binding == null) return
                val estaCarga = ++versionCarga
                val ids = snapshot.children.mapNotNull { child ->
                    child.child("idAnuncio").getValue(String::class.java)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() && Constantes.esClaveFirebaseValida(it) }
                }
                if (ids.isEmpty()) {
                    actualizarLista(emptyList())
                    return
                }

                val encontrados = arrayOfNulls<ModeloAnuncio>(ids.size)
                val pendientes = AtomicInteger(ids.size)
                ids.forEachIndexed { index, idAnuncio ->
                    FirebaseDatabase.getInstance().getReference("Anuncios").child(idAnuncio)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(anuncioSnapshot: DataSnapshot) {
                                if (!isAdded || _binding == null || estaCarga != versionCarga) return
                                encontrados[index] = try {
                                    anuncioSnapshot.getValue(ModeloAnuncio::class.java)
                                } catch (_: Exception) {
                                    null
                                }
                                if (pendientes.decrementAndGet() == 0) {
                                    actualizarLista(encontrados.filterNotNull())
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                if (!isAdded || _binding == null || estaCarga != versionCarga) return
                                android.util.Log.e("FavAnuncios", "No se pudo cargar el anuncio", error.toException())
                                if (pendientes.decrementAndGet() == 0) {
                                    actualizarLista(encontrados.filterNotNull())
                                }
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FavAnuncios", "No se pudieron cargar favoritos", error.toException())
            }
        }
        favoritosRef?.addValueEventListener(favoritosListener as ValueEventListener)
    }

    private fun actualizarLista(nuevaLista: List<ModeloAnuncio>) {
        val b = _binding ?: return
        anunciosArrayList.clear()
        anunciosArrayList.addAll(nuevaLista)
        b.tvVacio.visibility = if (anunciosArrayList.isEmpty()) View.VISIBLE else View.GONE
        anunciosAdaptador.filter.filter(b.EtBuscar.text?.toString().orEmpty())
    }

    override fun onDestroyView() {
        versionCarga++
        favoritosListener?.let { favoritosRef?.removeEventListener(it) }
        favoritosListener = null
        favoritosRef = null
        _binding = null
        super.onDestroyView()
    }

}
