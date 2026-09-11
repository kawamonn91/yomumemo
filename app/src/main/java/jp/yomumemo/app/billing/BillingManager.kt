package jp.yomumemo.app.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 購入状態の問い合わせ結果。 */
sealed interface OwnershipCheck {
    data class Known(val owned: Boolean) : OwnershipCheck

    /** Play に確認できなかった。控えている状態を維持する。 */
    data object Unavailable : OwnershipCheck
}

data class BillingUiState(
    val isConnected: Boolean = false,
    /** 表示用の価格文字列。地域ごとの通貨で Play が返したものをそのまま使う。 */
    val formattedPrice: String? = null,
    val isPurchaseInFlight: Boolean = false,
    val lastError: String? = null,
)

/**
 * Google Play の課金を扱う。
 *
 * 本アプリの商品は買い切りのプレミアム解除1点のみ(非消費型)。
 * 消費はしないので consumeAsync は使わず、acknowledge だけ行う。
 * **acknowledge を3日以内に行わないと自動返金される**ため、購入検知の度に必ず実行する。
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onPurchaseVerified: suspend (Boolean) -> Unit,
) {

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _state.update { it.copy(isPurchaseInFlight = false) }
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch { handlePurchases(purchases.orEmpty()) }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // 利用者が自分でやめた。エラー表示はしない。
                _state.update { it.copy(lastError = null) }
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 別端末などで購入済み。問い合わせ直せば権利が復元できる。
                scope.launch { refreshOwnership() }
            }

            else -> {
                Log.w(TAG, "購入に失敗: ${result.responseCode} ${result.debugMessage}")
                _state.update { it.copy(lastError = "購入手続きを完了できませんでした。") }
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                _state.update { it.copy(isConnected = ok) }
                if (ok) {
                    scope.launch {
                        loadProductDetails()
                        refreshOwnership()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() に任せる
                _state.update { it.copy(isConnected = false) }
            }
        })
    }

    private suspend fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "商品情報を取得できません: ${result.billingResult.debugMessage}")
            return
        }
        val details = result.productDetailsList?.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
        productDetails = details
        _state.update {
            it.copy(formattedPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice)
        }
    }

    /**
     * Play に購入状態を問い合わせる。
     * 通信できない場合に「未購入」と誤判定すると購入者の機能を奪ってしまうため、
     * 分からなかったことを [OwnershipCheck.Unavailable] として区別して返す。
     */
    suspend fun refreshOwnership(): OwnershipCheck {
        if (!client.isReady) return OwnershipCheck.Unavailable

        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return OwnershipCheck.Unavailable
        }

        handlePurchases(result.purchasesList)
        val owned = result.purchasesList.any { it.isPremiumPurchased() }
        onPurchaseVerified(owned)
        return OwnershipCheck.Known(owned)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (!purchase.isPremiumPurchased()) continue

            // 未承認のまま3日経つと自動返金されるので必ず承認する
            if (!purchase.isAcknowledged) {
                val ackResult = client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                )
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "購入の承認に失敗: ${ackResult.debugMessage}")
                }
            }
            onPurchaseVerified(true)
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.update { it.copy(lastError = "商品情報を取得できていません。通信状況を確認してください。") }
            return
        }
        _state.update { it.copy(isPurchaseInFlight = true, lastError = null) }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(isPurchaseInFlight = false, lastError = "購入画面を開けませんでした。")
            }
        }
    }

    fun clearError() = _state.update { it.copy(lastError = null) }

    private fun Purchase.isPremiumPurchased(): Boolean =
        products.contains(PREMIUM_PRODUCT_ID) && purchaseState == Purchase.PurchaseState.PURCHASED

    companion object {
        private const val TAG = "BillingManager"

        /** Play Console に登録する商品ID(非消費型の1回購入)。 */
        const val PREMIUM_PRODUCT_ID = "yomumemo_premium_lifetime"
    }
}
