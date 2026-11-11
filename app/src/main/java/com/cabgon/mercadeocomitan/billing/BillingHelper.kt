package com.cabgon.mercadeocomitan.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Pago único para publicar anuncios.
 * productId: 01publicar (configurado en Play Console)
 */
class BillingHelper(
    context: Context,
    private val productId: String = PRODUCT_ID,
) : PurchasesUpdatedListener {

    companion object {
        // Debe coincidir exactamente con el producto INAPP activo en Play Console.
        // Se centraliza para que publicar y restaurar consulten el mismo producto.
        const val PRODUCT_ID = "01publicar"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val mainHandler = Handler(Looper.getMainLooper())

    // Billing 8.0.0 → requiere PendingPurchasesParams
    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var cachedProduct: ProductDetails? = null
    private var onResult: ((Boolean, String?) -> Unit)? = null
    // para no llamar 2 veces al callback
    private var handlingPurchase = false

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /* 1) Conexión */
    fun startConnection(onReady: (Boolean) -> Unit) {
        if (billingClient.isReady) {
            runOnMain { onReady(true) }
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                runOnMain { onReady(false) }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                runOnMain { onReady(ok) }
            }
        })
    }

    /* 2) Cargar producto INAPP (único) */
    fun loadProduct(onLoaded: (Boolean) -> Unit) {
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                runOnMain { onLoaded(false) }
                return@queryProductDetailsAsync
            }

            val list = result.productDetailsList
            if (list.isEmpty()) {
                runOnMain { onLoaded(false) }
                return@queryProductDetailsAsync
            }

            cachedProduct = list[0]
            runOnMain { onLoaded(true) }
        }
    }

    /* 3) Lanzar compra */
    fun launchPurchase(activity: Activity, onResult: (ok: Boolean, error: String?) -> Unit) {
        val product = cachedProduct
        if (product == null) {
            runOnMain { onResult(false, "Producto no cargado") }
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        this.onResult = onResult
        billingClient.launchBillingFlow(activity, flowParams)
    }

    /* 4) Callback de compra */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when {
            (billingResult.responseCode == BillingClient.BillingResponseCode.OK) && (purchases != null) -> {
                purchases.forEach { handlePurchase(it) }
            }
            billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                runOnMain { onResult?.invoke(false, "Cancelado") }
            }
            else -> {
                runOnMain { onResult?.invoke(false, billingResult.debugMessage) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        // validar que sea nuestro producto
        if (!purchase.products.contains(productId)) return

        // evita doble proceso
        if (handlingPurchase) return
        handlingPurchase = true

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { ackResult ->
                        if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            grantEntitlement(purchase)
                        } else {
                            handlingPurchase = false
                            runOnMain { onResult?.invoke(false, ackResult.debugMessage) }
                        }
                    }
                } else {
                    grantEntitlement(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                handlingPurchase = false
                runOnMain {
                    onResult?.invoke(false, "Tu pago está pendiente de confirmación en Google Play")
                }
            }
            else -> {
                handlingPurchase = false
            }
        }
    }

    /* 5) Dar permiso en Firebase */
    private fun grantEntitlement(purchase: Purchase) {
        val uid = auth.uid ?: run {
            handlingPurchase = false
            runOnMain { onResult?.invoke(false, "Inicia sesión para completar la compra") }
            return
        }

        val updates = mapOf(
            "canPost" to true,
            "purchase/productId" to productId,
            "purchase/token" to purchase.purchaseToken,
            "purchase/orderId" to (purchase.orderId ?: ""),
            "purchase/time" to purchase.purchaseTime
        )
        db.child("Usuarios").child(uid)
            .updateChildren(updates)
            .addOnCompleteListener { tarea ->
                handlingPurchase = false
                runOnMain {
                    if (tarea.isSuccessful) onResult?.invoke(true, null)
                    else onResult?.invoke(false, "No se pudo guardar el permiso de publicación")
                }
            }
    }

    /**
     * 6) Restaurar al iniciar
     * onDone(hasPurchase, wasGrantedNow)
     *  - hasPurchase = el user sí tiene la compra en Google
     *  - wasGrantedNow = en Firebase NO estaba y lo activamos ahorita
     */
    fun queryAndRestoreEntitlement(onDone: (hasPurchase: Boolean, wasGrantedNow: Boolean) -> Unit) {
        val uid = auth.uid
        if (uid == null) {
            runOnMain { onDone(false, false) }
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { br, purchases ->
            if (br.responseCode != BillingClient.BillingResponseCode.OK) {
                runOnMain { onDone(false, false) }
                return@queryPurchasesAsync
            }

            val purchase = purchases.firstOrNull {
                it.products.contains(productId) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            if (purchase == null) {
                runOnMain { onDone(false, false) }
                return@queryPurchasesAsync
            }

            val restoreToFirebase = {
                db.child("Usuarios").child(uid).child("canPost").get()
                    .addOnSuccessListener { snap ->
                        val yaTenia = snap.getValue(Boolean::class.java) ?: false
                        if (yaTenia) {
                            runOnMain { onDone(true, false) }
                        } else {
                            val updates = mapOf(
                                "canPost" to true,
                                "purchase/productId" to productId,
                                "purchase/token" to purchase.purchaseToken,
                                "purchase/orderId" to (purchase.orderId ?: ""),
                                "purchase/time" to purchase.purchaseTime
                            )
                            db.child("Usuarios").child(uid)
                                .updateChildren(updates)
                                .addOnCompleteListener { tarea ->
                                    runOnMain { onDone(true, tarea.isSuccessful) }
                                }
                        }
                    }
                    .addOnFailureListener {
                        runOnMain { onDone(true, false) }
                    }
            }

            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(ackParams) {
                    restoreToFirebase()
                }
            } else {
                restoreToFirebase()
            }
        }
    }

    // opcional
    fun endConnection() {
        onResult = null
        handlingPurchase = false
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
