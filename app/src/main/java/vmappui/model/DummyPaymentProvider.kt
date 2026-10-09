package vmappui.model

import android.os.Handler
import android.os.Looper
import java.util.UUID

class DummyPaymentProvider(
    private val delayMs: Long = 1000L,
    private val shouldFail: Boolean = false
) : PaymentProvider {

    private val handler = Handler(Looper.getMainLooper())

    override fun processPayment(amount: Double, callback: PaymentProvider.PaymentCallback) {
        handler.postDelayed({
            if (shouldFail) {
                callback.onError("Dummy Payment Failed: Insufficient Funds")
            } else {
                val paymentInfo = PaymentInfo(
                    status = PaymentStatus.RECEIVED,
                    amount = amount,
                    provider = "DUMMY_PAYMENT_GATEWAY",
                    provider_ref = "TXN_${UUID.randomUUID().toString().take(8)}"
                )
                callback.onSuccess(paymentInfo)
            }
        }, delayMs)
    }
}