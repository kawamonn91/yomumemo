package jp.yomumemo.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.yomumemo.app.billing.BillingManager
import jp.yomumemo.app.billing.EntitlementRepository
import jp.yomumemo.app.billing.OwnershipCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaywallUiState(
    val isPremium: Boolean = false,
    val price: String? = null,
    val canPurchase: Boolean = false,
    val isPurchaseInFlight: Boolean = false,
    val error: String? = null,
    val restoreMessage: String? = null,
)

class PaywallViewModel(
    private val billing: BillingManager,
    private val entitlements: EntitlementRepository,
) : ViewModel() {

    private val restoreMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PaywallUiState> =
        combine(
            entitlements.isPremium,
            billing.state,
            restoreMessage,
        ) { premium, billingState, restore ->
            PaywallUiState(
                isPremium = premium,
                price = billingState.formattedPrice,
                canPurchase = billingState.isConnected && billingState.formattedPrice != null,
                isPurchaseInFlight = billingState.isPurchaseInFlight,
                error = billingState.lastError,
                restoreMessage = restore,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PaywallUiState(),
        )

    init {
        billing.connect()
    }

    fun purchase(activity: Activity) {
        restoreMessage.value = null
        billing.launchPurchase(activity)
    }

    /**
     * 購入の復元。機種変更や再インストール後に使う。
     * Play に確認できなかった場合は、未購入と断定せずそう伝える。
     */
    fun restore() {
        viewModelScope.launch {
            when (val check = billing.refreshOwnership()) {
                is OwnershipCheck.Known -> {
                    entitlements.applyOwnership(check)
                    restoreMessage.value = if (check.owned) {
                        "購入を確認しました。"
                    } else {
                        "このアカウントでの購入は見つかりませんでした。" +
                            "購入時と同じ Google アカウントでログインしているかご確認ください。"
                    }
                }

                OwnershipCheck.Unavailable ->
                    restoreMessage.value = "Google Play に接続できませんでした。通信状況をご確認ください。"
            }
        }
    }
}
