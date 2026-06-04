package com.example.recorderproject.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wraps Google Play Billing for the single one-time "MEAT REC Pro" lifetime unlock.
 *
 * Product type is INAPP (non-consumable). Source of truth for entitlement is
 * `queryPurchasesAsync` run on every connection; the result is mirrored to
 * [EntitlementStore] so the app knows its Pro status offline / before billing connects.
 *
 * Degrades gracefully: if Play Store / billing is unavailable (e.g. sideload, emulator
 * without Play), the manager simply reports no products and the app stays in Free mode.
 */
class BillingManager(
    context: Context,
    private val entitlements: EntitlementStore,
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        /** Must match the in-app product ID created in Play Console. */
        const val PRODUCT_PRO_LIFETIME = "meatrec_pro_lifetime"

        /** Launch promotion: the Play Console price IS the promo price; the struck-through
         *  "original" is computed as promoPrice / (1 - discount). 35% off → original = price / 0.65. */
        const val LAUNCH_DISCOUNT_PERCENT = 35

        /**
         * Fallback prices shown ONLY before Play Billing connects (pre-Play-Console setup,
         * emulator without Play, transient errors). Real installs get the exact, auto-localized
         * Play price via formattedPrice. Here we pick a sensible round price in the device's
         * own currency so non-Thai testers don't see Baht. Pair = (original, promo).
         */
        private val FALLBACK_BY_CURRENCY: Map<String, Pair<String, String>> = mapOf(
            "THB" to ("฿99"        to "฿69"),
            "USD" to ("$2.99"      to "$1.99"),
            "EUR" to ("€2.99"      to "€1.99"),
            "GBP" to ("£2.49"      to "£1.79"),
            "JPY" to ("¥459"       to "¥299"),
            "CNY" to ("¥21"        to "¥14"),
            "INR" to ("₹259"       to "₹169"),
            "KRW" to ("₩4,400"     to "₩2,900"),
            "IDR" to ("Rp49.000"   to "Rp32.000"),
            "VND" to ("₫75.000"    to "₫49.000"),
            "BRL" to ("R$14,90"    to "R$9,90"),
            "RUB" to ("289 ₽"      to "189 ₽"),
            "TRY" to ("₺105"       to "₺69"),
            "PHP" to ("₱179"       to "₱119"),
            "MYR" to ("RM13"       to "RM9"),
            "AUD" to ("$4.49"      to "$2.99"),
            "CAD" to ("$4.49"      to "$2.99"),
            "MXN" to ("$59"        to "39"),
        )

        private fun deviceCurrencyCode(): String = try {
            java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode
        } catch (_: Exception) { "USD" }

        /** (original, promo) fallback strings in the device's local currency; defaults to USD. */
        fun localizedFallback(): Pair<String, String> =
            FALLBACK_BY_CURRENCY[deviceCurrencyCode()] ?: FALLBACK_BY_CURRENCY.getValue("USD")

        val FALLBACK_PROMO_PRICE: String get() = localizedFallback().second
        val FALLBACK_ORIGINAL_PRICE: String get() = localizedFallback().first
    }

    private val appContext = context.applicationContext

    /** Localized promo price string from Play (e.g. "฿129.00"); null until product details load. */
    private val _priceText = MutableStateFlow<String?>(null)
    val priceText: StateFlow<String?> = _priceText

    /** Struck-through "original" price, computed from the live price + launch discount; null until loaded. */
    private val _originalPriceText = MutableStateFlow<String?>(null)
    val originalPriceText: StateFlow<String?> = _originalPriceText

    /** True once billing has connected and we've completed at least one purchase query. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /** Surfaced to UI for one-shot messages (purchase success / error). Null = nothing pending. */
    private val _lastMessage = MutableStateFlow<String?>(null)
    val lastMessage: StateFlow<String?> = _lastMessage
    fun consumeMessage() { _lastMessage.value = null }

    private var proProductDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        if (billingClient.isReady) {
            queryProduct()
            queryPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    queryProduct()
                    queryPurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    _ready.value = true // still "ready" — just no products (Free mode)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
                _ready.value = false
            }
        })
    }

    fun stop() {
        try { billingClient.endConnection() } catch (_: Exception) {}
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_PRO_LIFETIME)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val pd = details.firstOrNull { it.productId == PRODUCT_PRO_LIFETIME }
                proProductDetails = pd
                val offer = pd?.oneTimePurchaseOfferDetails
                _priceText.value = offer?.formattedPrice
                _originalPriceText.value = computeOriginalPrice(offer)
                Log.d(TAG, "Product loaded: price=${_priceText.value} was=${_originalPriceText.value}")
            } else {
                Log.w(TAG, "queryProduct failed: ${result.debugMessage}")
            }
        }
    }

    /**
     * Compute the struck-through "original" price for the launch promo from the live
     * Play price. original = promo / (1 - discount). Formats in the product's own currency
     * so it reads correctly in every region. Returns null if details aren't available.
     */
    private fun computeOriginalPrice(offer: ProductDetails.OneTimePurchaseOfferDetails?): String? {
        offer ?: return null
        return try {
            val promo = offer.priceAmountMicros / 1_000_000.0
            if (promo <= 0.0) return null
            val original = promo / (1.0 - LAUNCH_DISCOUNT_PERCENT / 100.0)
            val fmt = java.text.NumberFormat.getCurrencyInstance()
            fmt.currency = java.util.Currency.getInstance(offer.priceCurrencyCode)
            // Round up to a whole unit so it reads as a clean "199" rather than "198.46".
            fmt.maximumFractionDigits = 0
            fmt.format(kotlin.math.ceil(original))
        } catch (e: Exception) {
            Log.w(TAG, "computeOriginalPrice failed: ${e.message}")
            null
        }
    }

    /** Source of truth: ask Play what this account actually owns. */
    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val ownsPro = purchases.any { p ->
                    p.products.contains(PRODUCT_PRO_LIFETIME) &&
                        p.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                entitlements.setPro(ownsPro)
                // Acknowledge any unacknowledged Pro purchase (required within 3 days).
                purchases.forEach { acknowledgeIfNeeded(it) }
            }
            _ready.value = true
        }
    }

    /** Launch the Play purchase dialog for the Pro unlock. */
    fun launchPurchase(activity: Activity) {
        val pd = proProductDetails
        if (pd == null) {
            _lastMessage.value = "Store not ready — please try again in a moment."
            // Try to (re)load the product so the next tap works.
            queryProduct()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            ).build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Silent — user backed out.
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                entitlements.setPro(true)
                _lastMessage.value = "You already own MEAT REC Pro — unlocked."
            }
            else -> {
                _lastMessage.value = "Purchase failed: ${result.debugMessage.ifBlank { "unknown error" }}"
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_PRO_LIFETIME) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        ) {
            entitlements.setPro(true)
            _lastMessage.value = "MEAT REC Pro unlocked — thank you!"
            acknowledgeIfNeeded(purchase)
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                Log.d(TAG, "Acknowledge: ${result.responseCode}")
            }
        }
    }
}
