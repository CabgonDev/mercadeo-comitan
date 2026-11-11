package com.cabgon.mercadeocomitan.Fragmentos

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cabgon.mercadeocomitan.Adaptadores.AdaptadorChats
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloChats
import com.cabgon.mercadeocomitan.databinding.FragmentChatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FragmentChats : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var mContext: Context
    private lateinit var firebaseAuth: FirebaseAuth
    private var miUid: String? = null

    private val chatsArrayList = ArrayList<ModeloChats>()
    private val modelosPorChat = LinkedHashMap<String, ModeloChats>()
    private lateinit var adaptadorChats: AdaptadorChats

    // listener directo a /Chats
    private var chatsRef: DatabaseReference? = null
    private var chatsListener: ChildEventListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseAuth = FirebaseAuth.getInstance()
        miUid = firebaseAuth.uid

        // Recycler
        adaptadorChats = AdaptadorChats(mContext, chatsArrayList)
        binding.chatsRv.layoutManager = LinearLayoutManager(mContext)
        binding.chatsRv.adapter = adaptadorChats

        // Buscador (tu adaptador ya es Filterable)
        binding.EtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(cs: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    adaptadorChats.filter.filter(cs?.toString() ?: "")
                } catch (_: Exception) {}
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (miUid == null) {
            Toast.makeText(mContext, "Sesión expirada", Toast.LENGTH_SHORT).show()
            return
        }

        cargarMisChatsDesdeChats()
    }

    /**
     * Lee DIRECTO de /Chats y arma la lista con los chats donde estoy yo.
     * No usa /UserChats.
     */
    private fun cargarMisChatsDesdeChats() {
        val uid = miUid ?: return

        // Limpia por si se recrea el fragment
        chatsListener?.let { chatsRef?.removeEventListener(it) }
        modelosPorChat.clear()
        chatsArrayList.clear()
        adaptadorChats.submitList(ArrayList())

        chatsRef = FirebaseDatabase.getInstance().getReference("Chats")
        chatsListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                actualizarChat(snapshot, uid)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                actualizarChat(snapshot, uid)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { modelosPorChat.remove(it) }
                publicarChats()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    Toast.makeText(mContext, "No se pudieron cargar los chats: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        chatsRef!!.addChildEventListener(chatsListener as ChildEventListener)
    }

    private fun actualizarChat(snapshot: DataSnapshot, uid: String) {
        if (!isAdded || _binding == null) return
        val chatId = snapshot.key ?: return
        if (!Constantes.esClaveFirebaseValida(chatId)) return

        val modelo = construirModeloDesdeChat(chatId, snapshot, uid)
        if (modelo == null) modelosPorChat.remove(chatId) else modelosPorChat[chatId] = modelo
        publicarChats()
    }

    private fun publicarChats() {
        if (!isAdded || _binding == null) return
        chatsArrayList.clear()
        chatsArrayList.addAll(modelosPorChat.values.sortedByDescending { it.tiempo })
        binding.TxtVacio.visibility = if (chatsArrayList.isEmpty()) View.VISIBLE else View.GONE
        adaptadorChats.submitList(ArrayList(chatsArrayList))
        val filtroActual = binding.EtBuscar.text?.toString().orEmpty()
        if (filtroActual.isNotBlank()) {
            adaptadorChats.filter.filter(filtroActual)
        }
    }

    /**
     * Construye el modelo usando solo mensajes donde el usuario autenticado
     * participa de forma explícita. Esto también soporta chats legacy con ID push.
     */
    private fun construirModeloDesdeChat(
        chatId: String,
        chatSnap: DataSnapshot,
        miUid: String
    ): ModeloChats? {
        // Busca el último mensaje de la pareja exacta. El nombre del nodo no es
        // una garantía suficiente porque también existen chats legacy con IDs push.
        val mensajesSnap = chatSnap.child("mensajes")
        var ultimoTiempo = 0L
        var otroUid = ""
        var ultimoMensaje: DataSnapshot? = null

        for (msg in mensajesSnap.children) {
            val emisor = msg.child("emisorUid").getValue(String::class.java) ?: ""
            val receptor = msg.child("receptorUid").getValue(String::class.java) ?: ""
            val t = msg.child("tiempo").getValue(Long::class.java) ?: 0L

            val otro = when {
                emisor == miUid -> receptor
                receptor == miUid -> emisor
                else -> continue
            }
            if (!Constantes.esClaveFirebaseValida(otro) || otro == miUid) continue

            if (t >= ultimoTiempo) {
                ultimoTiempo = t
                otroUid = otro
                ultimoMensaje = msg
            }
        }

        // si no encontramos con quién hablo, no lo agregamos
        if (otroUid.isEmpty()) return null

        return ModeloChats().apply {
            keyChat = chatId
            uidRecibimos = otroUid
            tiempo = ultimoTiempo
            ultimoMensaje?.let { msg ->
                idMensaje = msg.child("idMensaje").getValue(String::class.java) ?: msg.key.orEmpty()
                mensaje = msg.child("mensaje").getValue(String::class.java).orEmpty()
                tipoMensaje = msg.child("tipoMensaje").getValue(String::class.java).orEmpty()
                emisorUid = msg.child("emisorUid").getValue(String::class.java).orEmpty()
                receptorUid = msg.child("receptorUid").getValue(String::class.java).orEmpty()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatsListener?.let { chatsRef?.removeEventListener(it) }
        chatsListener = null
        chatsRef = null
        modelosPorChat.clear()
        chatsArrayList.clear()
        _binding = null
    }
}
