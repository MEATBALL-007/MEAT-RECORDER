package com.example.recorderproject.billing

import android.content.Context
import com.example.recorderproject.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the user's Pro entitlement. Cached in its own SharedPreferences (NOT the wipeable
 * settings DataStore) so a factory-reset of recording settings never revokes a paid unlock.
 *
 * Play Billing's `queryPurchasesAsync` is the real source of truth and overwrites this
 * cache on every launch via [BillingManager]; the cache only covers the offline / pre-connect
 * window so gated UI doesn't flicker.
 */
class EntitlementStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("meatrec_entitlements", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(readInitial())
    val isPro: StateFlow<Boolean> = _isPro

    private fun readInitial(): Boolean {
        // Debug builds can force-unlock for testing without a Play Console product.
        if (BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_FORCE_PRO, false)) return true
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun setPro(value: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, value).apply()
        // Don't downgrade a debug-forced unlock.
        if (BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_FORCE_PRO, false)) {
            _isPro.value = true
        } else {
            _isPro.value = value
        }
    }

    /** Debug-only: simulate a Pro purchase so gating can be exercised before Play Console setup. */
    fun setDebugForcePro(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_DEBUG_FORCE_PRO, value).apply()
        _isPro.value = value || prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun isDebugForcePro(): Boolean = BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_FORCE_PRO, false)

    companion object {
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_DEBUG_FORCE_PRO = "debug_force_pro"
    }
}
