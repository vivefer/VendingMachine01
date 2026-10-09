package vmappui.model

object PaymentProviderFactory {

    fun create(): PaymentProvider {
        // Returns dummy provider now; easily swappable to Fonepay later
        return DummyPaymentProvider()
    }
}