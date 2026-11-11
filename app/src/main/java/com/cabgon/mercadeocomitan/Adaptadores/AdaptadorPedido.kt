@file:Suppress("PackageName")

package com.cabgon.mercadeocomitan.Adaptadores

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cabgon.mercadeocomitan.Constantes
import com.cabgon.mercadeocomitan.Modelo.ModeloOrden
import com.cabgon.mercadeocomitan.Pedidos.PresentacionOrden
import com.cabgon.mercadeocomitan.Pedidos.TonoEstadoOrden
import com.cabgon.mercadeocomitan.R
import com.cabgon.mercadeocomitan.databinding.ItemPedidoBinding

class AdaptadorPedido(
    private val uidActualProvider: () -> String,
    private val onPedidoClick: (ModeloOrden) -> Unit,
) : ListAdapter<ModeloOrden, AdaptadorPedido.HolderPedido>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPedido {
        val binding = ItemPedidoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HolderPedido(binding)
    }

    override fun onBindViewHolder(holder: HolderPedido, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HolderPedido(
        private val binding: ItemPedidoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(orden: ModeloOrden) {
            val context = binding.root.context
            val uid = uidActualProvider()
            val esVenta = uid.isNotBlank() && uid == orden.vendedorId

            // Rol (Compra vs Venta)
            if (esVenta) {
                binding.tvRolOrden.text = "Venta"
                binding.tvRolOrden.setBackgroundResource(R.drawable.bg_chip_teal)
                binding.tvRolOrden.setTextColor(
                    ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)
                )
            } else {
                binding.tvRolOrden.text = "Compra"
                binding.tvRolOrden.setBackgroundResource(R.drawable.bg_chip_blue)
                binding.tvRolOrden.setTextColor(
                    ContextCompat.getColor(context, R.color.md_theme_onSecondaryContainer)
                )
            }

            // Estado con color semántico
            binding.tvEstadoOrden.text = PresentacionOrden.textoEstadoCorto(orden.estado)
            when (PresentacionOrden.tonoEstado(orden.estado)) {
                TonoEstadoOrden.PENDIENTE -> {
                    binding.tvEstadoOrden.setBackgroundResource(R.drawable.bg_chip_amber)
                    binding.tvEstadoOrden.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_onTertiaryContainer)
                    )
                }
                TonoEstadoOrden.EN_PROGRESO -> {
                    binding.tvEstadoOrden.setBackgroundResource(R.drawable.bg_chip_teal)
                    binding.tvEstadoOrden.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)
                    )
                }
                TonoEstadoOrden.COMPLETADO -> {
                    binding.tvEstadoOrden.setBackgroundResource(R.drawable.bg_chip_success)
                    binding.tvEstadoOrden.setTextColor(
                        ContextCompat.getColor(context, R.color.success_color)
                    )
                }
                TonoEstadoOrden.ALERTA -> {
                    binding.tvEstadoOrden.setBackgroundResource(R.drawable.bg_chip_error)
                    binding.tvEstadoOrden.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_onErrorContainer)
                    )
                }
            }

            // Fecha de última actualización o creación
            val timestamp = orden.actualizadaEn.takeIf { it > 0L } ?: orden.creadaEn
            if (timestamp > 0L) {
                binding.tvFechaOrden.visibility = View.VISIBLE
                binding.tvFechaOrden.text = Constantes.obtenerFecha(timestamp)
            } else {
                binding.tvFechaOrden.visibility = View.GONE
            }

            // Título y precio
            binding.tvTituloOrden.text = orden.tituloAnuncio.ifBlank { "Pedido" }
            binding.tvPrecioOrden.text = PresentacionOrden.formatearCentavos(orden.importeCentavos)

            // Método de pago
            binding.tvMetodoPagoOrden.text = PresentacionOrden.textoMetodoPagoResumen(orden)

            // Punto de encuentro o fecha de entrega programada
            val detalleEntrega = PresentacionOrden.textoDetalleEntregaOPunto(orden)
            if (detalleEntrega.isNotBlank()) {
                binding.llDetalleEntregaOrden.visibility = View.VISIBLE
                binding.tvDetalleEntregaOrden.text = detalleEntrega
            } else {
                binding.llDetalleEntregaOrden.visibility = View.GONE
            }

            // Sugerencia de siguiente paso
            binding.tvSugerenciaAccion.text = PresentacionOrden.textoSugerenciaAccion(orden, uid)

            val clickAction = View.OnClickListener { onPedidoClick(orden) }
            binding.cardPedido.setOnClickListener(clickAction)
            binding.btnAbrirPedido.setOnClickListener(clickAction)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ModeloOrden>() {
        override fun areItemsTheSame(oldItem: ModeloOrden, newItem: ModeloOrden): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ModeloOrden, newItem: ModeloOrden): Boolean =
            oldItem == newItem
    }
}

