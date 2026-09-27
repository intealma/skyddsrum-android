package io.github.intealma.skyddsrum.premium

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import io.github.intealma.skyddsrum.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RevenueCat integration. Premium is an extra, never a gate on safety features:
 * finding, listing and navigating to shelters is always free.
 */
object Premium {
    /** Entitlement configured in the RevenueCat dashboard. */
    const val ENTITLEMENT_ID = "premium"
    private const val TAG = "Premium"

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** False when the build has no RevenueCat API key; the paywall is then replaced by an explanation. */
    val isConfigured: Boolean get() = Purchases.isConfigured

    fun init(app: Application) {
        val apiKey = BuildConfig.REVENUECAT_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "REVENUECAT_API_KEY is empty; purchases are disabled. See README.")
            return
        }
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        // Fires after purchases, restores and renewals, so features unlock live.
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener(::update)
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { Log.w(TAG, "getCustomerInfo failed: ${it.message}") },
            onSuccess = ::update,
        )
    }

    fun update(info: CustomerInfo) {
        _isPremium.value = info.entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    /** Restores purchases; returns whether premium is active afterwards. */
    suspend fun restore(): Boolean {
        val info = Purchases.sharedInstance.awaitRestore()
        update(info)
        return _isPremium.value
    }
}
