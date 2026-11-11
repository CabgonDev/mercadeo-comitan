package com.cabgon.mercadeocomitan.Fragmentos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cabgon.mercadeocomitan.AuthManager
import com.cabgon.mercadeocomitan.CambiarPasword
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.EditarPerfil
import com.cabgon.mercadeocomitan.Eliminar_cuenta
import com.cabgon.mercadeocomitan.PanelAdminActivity
import com.cabgon.mercadeocomitan.PedidosActivity
import com.cabgon.mercadeocomitan.Modelo.esClabeValida
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.FragmentCuentaBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class FragmentCuenta : Fragment() {

    private var _binding: FragmentCuentaBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var mContext: Context
    private lateinit var progressDialog: com.cabgon.mercadeocomitan.ProgressDialogCompat

    private var userRef: DatabaseReference? = null
    private var userListener: ValueEventListener? = null
    private var actualizandoSwitchContacto = false
    private var datosBancariosCargados = false

    override fun onAttach(context: Context) {
        mContext = context
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCuentaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressDialog = com.cabgon.mercadeocomitan.ProgressDialogCompat(mContext).apply {
            setTitle(getString(R.string.progress_title))
            setCanceledOnTouchOutside(false)
        }

        firebaseAuth = FirebaseAuth.getInstance()

        leerInfo()

        // Botones de acciones
        binding.BtnEditarPerfil.setOnClickListener {
            startActivity(Intent(mContext, EditarPerfil::class.java))
        }

        binding.BtnCambiarPass.setOnClickListener {
            startActivity(Intent(mContext, CambiarPasword::class.java))
        }

        binding.BtnVerificarCuenta.setOnClickListener {
            verificarCuenta()
        }

        binding.BtnEliminarAnuncios.setOnClickListener {
            MaterialAlertDialogBuilder(mContext)
                .setTitle("Eliminar todos mis anuncios")
                .setMessage("¿Estás segur@ que deseas eliminar todos tus anuncios?")
                .setPositiveButton("Eliminar") { _, _ -> eliminarTodosMisAnuncios() }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.BtnMisPedidos.setOnClickListener {
            startActivity(Intent(mContext, PedidosActivity::class.java))
        }

        binding.BtnGuardarTransferencia.setOnClickListener {
            guardarDatosTransferencia()
        }

        binding.BtnEliminarCuenta.setOnClickListener {
            startActivity(Intent(mContext, Eliminar_cuenta::class.java))
        }

        binding.BtnCerrarSesion.setOnClickListener {
            MaterialAlertDialogBuilder(mContext)
                .setTitle("Cerrar sesión")
                .setMessage("¿Seguro que deseas cerrar sesión?")
                .setPositiveButton("Sí, cerrar") { _, _ ->
                    AuthManager.logout(requireActivity())
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // 👉 click del panel admin (lo mostramos/ocultamos al leer Firebase)
        binding.btnPanelAdmin.setOnClickListener {
            startActivity(Intent(requireContext(), PanelAdminActivity::class.java))
        }

        // Switch de habilitar contacto
        binding.SwHabilitarContacto.setOnCheckedChangeListener { _, isChecked ->
            if (actualizandoSwitchContacto) return@setOnCheckedChangeListener
            val uid = firebaseAuth.uid ?: return@setOnCheckedChangeListener
            val ref = FirebaseDatabase.getInstance()
                .getReference("Usuarios")
                .child(uid)
            ref.child("habilitarContacto").setValue(isChecked)
                .addOnFailureListener { e ->
                    if (isAdded)
                        Toast.makeText(mContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun leerInfo() {
        val uid = firebaseAuth.uid?.takeIf { it.isNotBlank() && Constantes.esClaveFirebaseValida(it) } ?: return
        userRef = FirebaseDatabase.getInstance()
            .getReference("Usuarios")
            .child(uid)

        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b = _binding ?: return
                if (!isAdded) return

                val nombres = snapshot.child("nombres").getValue(String::class.java).orEmpty()
                val email = snapshot.child("email").getValue(String::class.java).orEmpty()
                val imagen = snapshot.child("urlImagenPerfil").getValue(String::class.java).orEmpty()
                val fNac = snapshot.child("fecha_nac").getValue(String::class.java).orEmpty()
                val telefono = snapshot.child("telefono").getValue(String::class.java).orEmpty()
                val codTel = snapshot.child("codigoTelefono").getValue(String::class.java).orEmpty()
                val provedor = snapshot.child("provedor").getValue(String::class.java).orEmpty()
                val habilitarContacto =
                    snapshot.child("habilitarContacto").getValue(Boolean::class.java) ?: true
                val transferenciaHabilitada =
                    snapshot.child("transferenciaHabilitada").getValue(Boolean::class.java) ?: false

                // 👇 aquí leemos el rol y el bloqueo
                val rol = snapshot.child("rol").getValue(String::class.java)
                val bloqueado = snapshot.child("bloqueado").getValue(Boolean::class.java) ?: false

                if (bloqueado) {
                    Toast.makeText(requireContext(), "Tu cuenta fue bloqueada por un administrador.", Toast.LENGTH_LONG).show()
                    activity?.let { AuthManager.logout(it) } ?: firebaseAuth.signOut()
                    return
                }

                // mostrar/ocultar el botón admin
                mostrarOcultarPanelAdmin(rol)

                val tiempoMs = when (val rawTiempo = snapshot.child("tiempo").value) {
                    is Number -> rawTiempo.toLong()
                    is String -> rawTiempo.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val forTiempo = if (tiempoMs > 0L) Constantes.obtenerFecha(tiempoMs) else "—"
                val telefonoCompleto = buildString {
                    if (codTel.isNotBlank()) {
                        append(codTel.trim())
                        if (telefono.isNotBlank()) append(" ")
                    }
                    if (telefono.isNotBlank()) append(telefono.trim())
                }.ifBlank { "No registrado" }

                b.TvEmail.text = email.ifBlank { firebaseAuth.currentUser?.email.orEmpty() }
                b.TvNombres.text = nombres.ifBlank { "Usuario" }
                b.TvNombresAux.text = nombres.ifBlank { "Sin especificar" }
                b.TvNacimiento.text = fNac.ifBlank { "No especificada" }
                b.TvTelefono.text = telefonoCompleto
                b.TvMiembro.text = forTiempo

                actualizandoSwitchContacto = true
                b.SwHabilitarContacto.isChecked = habilitarContacto
                actualizandoSwitchContacto = false

                if (!datosBancariosCargados) {
                    b.SwTransferenciaHabilitada.isChecked = transferenciaHabilitada
                }
                b.TvEstadoTransferencia.text = if (transferenciaHabilitada) {
                    "Transferencia habilitada"
                } else {
                    "Configura una CLABE de 18 dígitos para habilitarla"
                }
                if (!datosBancariosCargados &&
                    !b.EtClabeTransferencia.hasFocus() &&
                    !b.EtBancoTransferencia.hasFocus()
                ) {
                    cargarDatosBancariosPrivados(uid)
                }

                try {
                    if (imagen.isNotBlank()) {
                        Glide.with(this@FragmentCuenta)
                            .load(imagen)
                            .placeholder(R.drawable.img_perfil)
                            .error(R.drawable.img_perfil)
                            .into(b.IvPerfil)
                    } else {
                        b.IvPerfil.setImageResource(R.drawable.img_perfil)
                    }
                } catch (_: Exception) {
                }

                if (provedor.equals("Email", ignoreCase = true)) {
                    val esVerificado = firebaseAuth.currentUser?.isEmailVerified == true
                    if (esVerificado) {
                        b.BtnVerificarCuenta.visibility = View.GONE
                        b.TvEstadoCuenta.text = "Verificado"
                    } else {
                        b.BtnVerificarCuenta.visibility = View.VISIBLE
                        b.TvEstadoCuenta.text = "No Verificado"
                    }
                } else {
                    b.BtnVerificarCuenta.visibility = View.GONE
                    b.BtnCambiarPass.visibility = View.GONE
                    b.TvEstadoCuenta.text = "Verificado"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        userRef!!.addValueEventListener(userListener as ValueEventListener)
    }

    private fun cargarDatosBancariosPrivados(uid: String) {
        if (!Constantes.esClaveFirebaseValida(uid)) return
        FirebaseDatabase.getInstance().getReference("CuentasTransferencia").child(uid).get()
            .addOnSuccessListener { snapshot ->
                val b = _binding ?: return@addOnSuccessListener
                if (!isAdded) return@addOnSuccessListener
                datosBancariosCargados = true
                if (!b.EtClabeTransferencia.hasFocus()) {
                    b.EtClabeTransferencia.setText(
                        snapshot.child("clabeInterbancaria").getValue(String::class.java).orEmpty()
                    )
                }
                if (!b.EtBancoTransferencia.hasFocus()) {
                    b.EtBancoTransferencia.setText(
                        snapshot.child("banco").getValue(String::class.java).orEmpty()
                    )
                }
            }
    }

    private fun guardarDatosTransferencia() {
        val uid = firebaseAuth.uid ?: return
        val clabe = binding.EtClabeTransferencia.text?.toString()?.trim().orEmpty()
        val banco = binding.EtBancoTransferencia.text?.toString()?.trim().orEmpty()
        val habilitada = binding.SwTransferenciaHabilitada.isChecked

        if (habilitada && !esClabeValida(clabe)) {
            binding.EtClabeTransferencia.error = "Revisa los 18 dígitos y el dígito verificador de la CLABE"
            binding.EtClabeTransferencia.requestFocus()
            return
        }
        if (habilitada && banco.isBlank()) {
            binding.EtBancoTransferencia.error = "Indica el banco receptor"
            binding.EtBancoTransferencia.requestFocus()
            return
        }

        val titular = binding.TvNombres.text?.toString().orEmpty()
        val cambios = hashMapOf<String, Any>(
            "CuentasTransferencia/$uid/clabeInterbancaria" to clabe,
            "CuentasTransferencia/$uid/banco" to banco,
            "CuentasTransferencia/$uid/titular" to titular,
            "CuentasTransferencia/$uid/transferenciaHabilitada" to habilitada,
            "CuentasTransferencia/$uid/actualizadoEn" to System.currentTimeMillis(),
            "Usuarios/$uid/transferenciaHabilitada" to habilitada,
        )
        FirebaseDatabase.getInstance().reference.updateChildren(cambios)
            .addOnSuccessListener {
                val b = _binding ?: return@addOnSuccessListener
                if (!isAdded) return@addOnSuccessListener
                datosBancariosCargados = true
                b.TvEstadoTransferencia.text = if (habilitada) {
                    "Transferencia habilitada · $banco"
                } else {
                    "Transferencia deshabilitada"
                }
                Toast.makeText(mContext, "Datos de transferencia guardados", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                if (isAdded) {
                    Toast.makeText(mContext, "No se guardaron los datos: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun eliminarTodosMisAnuncios() {
        val miUid = firebaseAuth.uid?.takeIf { it.isNotBlank() } ?: return
        val ref = FirebaseDatabase.getInstance()
            .getReference("Anuncios")
            .orderByChild("uid")
            .equalTo(miUid)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (ds in snapshot.children) ds.ref.removeValue()
                if (isAdded)
                    Toast.makeText(mContext, "Se han eliminado todos sus anuncios", Toast.LENGTH_SHORT).show()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun verificarCuenta() {
        val user = firebaseAuth.currentUser ?: run {
            Toast.makeText(mContext, "Tu sesión expiró. Inicia sesión nuevamente.", Toast.LENGTH_SHORT).show()
            return
        }
        progressDialog.setMessage("Enviando instrucciones de verificación a su correo electrónico")
        progressDialog.show()

        user.sendEmailVerification()
            .addOnSuccessListener {
                progressDialog.dismiss()
                if (isAdded) {
                    Toast.makeText(
                        mContext,
                        "Las instrucciones fueron enviadas a su correo registrado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                if (isAdded) {
                    Toast.makeText(mContext, "${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        progressDialog.dismiss()
        userListener?.let { listener -> userRef?.removeEventListener(listener) }
        userListener = null
        userRef = null
        datosBancariosCargados = false
        _binding = null
        super.onDestroyView()
    }

    private fun mostrarOcultarPanelAdmin(rol: String?) {
        val b = _binding ?: return
        val esAdmin = rol == Constantes.ROL_ADMIN || rol == Constantes.ROL_SUPERADMIN
        b.btnPanelAdmin.visibility = if (esAdmin) View.VISIBLE else View.GONE
    }
}
