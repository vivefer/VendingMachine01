package vmappui.model

interface PaymentProvider {

    interface PaymentCallback {
        fun onSuccess(paymentInfo: PaymentInfo)
        fun onError(error: String)
    }

    fun processPayment(amount: Double, callback: PaymentCallback)
}