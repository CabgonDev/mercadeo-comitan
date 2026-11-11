@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Fragmentos

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorAnuncio
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloAnuncio
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.FragmentMisAnunciosPublicadosBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class Mis_Anuncios_Publicados_Fragment : Fragment() {

    private var _binding: FragmentMisAnunciosPublicadosBinding? = null
    private val binding get() = _binding!!
    private lateinit var mContext: Context
    private lateinit var firebaseAuth: FirebaseAuth
    private val anunciosArrayList = ArrayList<ModeloAnuncio>()
    private lateinit var anunciosAdaptador: AdaptadorAnuncio
    private var misAnunciosQuery: Query? = null
    private var misAnunciosListener: ValueEventListener? = null

    override fun onAttach(context: Context) {
        this.mContext = context
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMisAnunciosPublicadosBinding.inflate(inflater, container, false)
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
        binding.misAnunciosRv.adapter = anunciosAdaptador
        cargarMisAnuncios()

        binding.EtBuscar.addTextChangedListener(object : TextWatcher {
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

    private fun cargarMisAnuncios() {
        val uid = firebaseAuth.uid?.takeIf { it.isNotBlank() && Constantes.esClaveFirebaseValida(it) } ?: run {
            _binding?.tvVacio?.visibility = View.VISIBLE
            return
        }
        misAnunciosListener?.let { misAnunciosQuery?.removeEventListener(it) }
        val ref = FirebaseDatabase.getInstance().getReference("Anuncios")
        misAnunciosQuery = ref.orderByChild("uid").equalTo(uid)
        misAnunciosListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b = _binding ?: return
                if (!isAdded) return
                anunciosArrayList.clear()

                for (ds in snapshot.children) {
                    try {
                        val modeloAnuncio = ds.getValue(ModeloAnuncio::class.java)
                        if (modeloAnuncio != null) {
                            anunciosArrayList.add(modeloAnuncio)
                        }
                    } catch (_: Exception) {
                    }
                }

                b.tvAnunciosCount.text =
                    mContext.getString(R.string.anuncios_publicados_count, anunciosArrayList.size)
                b.tvVacio.visibility = if (anunciosArrayList.isEmpty()) View.VISIBLE else View.GONE
                anunciosAdaptador.filter.filter(b.EtBuscar.text?.toString().orEmpty())
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("MisAnuncios", "No se pudieron cargar los anuncios", error.toException())
            }
        }
        misAnunciosQuery?.addValueEventListener(misAnunciosListener as ValueEventListener)
    }

    override fun onDestroyView() {
        misAnunciosListener?.let { misAnunciosQuery?.removeEventListener(it) }
        misAnunciosListener = null
        misAnunciosQuery = null
        _binding = null
        super.onDestroyView()
    }

}
