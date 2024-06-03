package com.blockstream.common.models.send

import com.blockstream.common.TransactionSegmentation
import com.blockstream.common.TransactionType
import com.blockstream.common.data.Banner
import com.blockstream.common.data.GreenWallet
import com.blockstream.common.data.NavData
import com.blockstream.common.events.Event
import com.blockstream.common.extensions.ifConnected
import com.blockstream.common.extensions.isBlank
import com.blockstream.common.extensions.isNotBlank
import com.blockstream.common.extensions.launchIn
import com.blockstream.common.extensions.previewAccountAsset
import com.blockstream.common.extensions.previewWallet
import com.blockstream.common.gdk.data.AccountAsset
import com.blockstream.common.gdk.params.AddressParams
import com.blockstream.common.gdk.params.CreateTransactionParams
import com.blockstream.common.navigation.NavigateDestinations
import com.blockstream.common.sideeffects.SideEffects
import com.blockstream.common.utils.feeRateWithUnit
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

abstract class RedepositViewModelAbstract(
    greenWallet: GreenWallet,
    accountAsset: AccountAsset,
) : CreateTransactionViewModelAbstract(
        greenWallet = greenWallet,
        accountAssetOrNull = accountAsset
    ) {
    override fun screenName(): String = "Redeposit"

    override fun segmentation(): HashMap<String, Any>? {
        return countly.accountSegmentation(session = session, account = account)
    }

}

class RedepositViewModel(
    greenWallet: GreenWallet,
    accountAsset: AccountAsset,
    private val isRedeposit2FA: Boolean
) : RedepositViewModelAbstract(greenWallet = greenWallet, accountAsset = accountAsset) {

    init {

        _navData.value = NavData(
            title = if (isRedeposit2FA) "id_re_enable_2fa" else "id_redeposit",
            subtitle = greenWallet.name
        )

        if (account.isLightning) {
            postSideEffect(
                SideEffects.NavigateBack(
                    title = "Lightning",
                    message = "Lightning redeposit is not supported"
                )
            )
        } else {
            session.ifConnected {
                _showFeeSelector.value = true
                _network.value = accountAsset.account.network

                combine(_feeEstimation.filterNotNull(), _feePriorityPrimitive) { _ ->
                    createTransactionParams.value = createTransactionParams()
                }.launchIn(this)
            }
        }

        bootstrap()
    }


    override fun handleEvent(event: Event) {
        super.handleEvent(event)

        when (event) {
            is LocalEvents.SignTransaction -> {
                signAndSendTransaction(
                    originalParams = createTransactionParams.value,
                    originalTransaction = createTransaction.value,
                    segmentation = TransactionSegmentation(
                        transactionType = TransactionType.REDEPOSIT,
                    ),
                    broadcast = event.broadcastTransaction
                )
            }
        }
    }

    override suspend fun createTransactionParams(): CreateTransactionParams {
        val unspentOutputs = session.getUnspentOutputs(account = account, isExpired = isRedeposit2FA)

        return AddressParams(
            address = session.getReceiveAddress(account).address,
            satoshi = 0,
            isGreedy = true,
            assetId = account.network.policyAssetOrNull?.takeIf { account.isLiquid }
        ).let { params ->
            CreateTransactionParams(
                from = accountAsset.value,
                addressees = listOf(params.toJsonElement()),
                addresseesAsParams = listOf(params),
                feeRate = getFeeRate(),
                utxos = unspentOutputs.unspentOutputsAsJsonElement
            )
        }.also {
            createTransactionParams.value = it
        }
    }

    override fun createTransaction(
        params: CreateTransactionParams?,
        finalCheckBeforeContinue: Boolean
    ) {
        doAsync({
            if(params == null){
                return@doAsync null
            }

            accountAsset.value?.let { accountAsset ->
                val network = accountAsset.account.network

                val tx = session.createTransaction(network, params)

                // Clear error as soon as possible
                if (tx.error.isBlank()) {
                    _error.value = null
                }

                tx.fee?.takeIf { it != 0L || tx.error.isNullOrBlank() }.also {
                    _feePriority.value = calculateFeePriority(
                        session = session,
                        feePriority = _feePriority.value,
                        feeAmount = it,
                        feeRate = tx.feeRate?.feeRateWithUnit()
                    )
                }

                tx.error.takeIf { it.isNotBlank() }?.also {
                    throw Exception(it)
                }

                tx
            }

        }, mutex = createTransactionMutex, preAction = {
            onProgress.value = true
            _isValid.value = false
        }, onSuccess = {
            createTransaction.value = it
            _isValid.value = it != null
            _error.value = null

            if(finalCheckBeforeContinue && params != null && it != null){
                session.pendingTransaction = Triple(params, it, TransactionSegmentation(
                    transactionType = TransactionType.SEND,
                    addressInputType = _addressInputType,
                    sendAll = true
                ))

                postSideEffect(SideEffects.NavigateTo(NavigateDestinations.SendConfirm(
                    accountAsset = accountAsset.value!!,
                    denomination = denomination.value
                )))
            }
        }, onError = {
            createTransaction.value = null
            _isValid.value = false
            _error.value = it.message
        })
    }
}

class RedepositViewModelPreview(greenWallet: GreenWallet, accountAsset: AccountAsset) : RedepositViewModelAbstract(greenWallet = greenWallet, accountAsset = accountAsset) {

    init {
        _showFeeSelector.value = true
        banner.value = Banner.preview3
    }


    companion object {
        fun preview() = RedepositViewModelPreview(previewWallet(), previewAccountAsset())
    }
}