package vmappui.model

import org.json.JSONArray
import org.json.JSONObject

enum class PaymentStatus { WAITING, RECEIVED, FAILED }
enum class FulfillmentState { NOT_STARTED, IN_PROGRESS, DISPENSING, DONE, PARTIAL, FAILED }
enum class SyncStatus { PENDING, SYNCED, FAILED }
enum class DispenseOutcome { PENDING, SUCCESS, FAILED }
enum class PendingClaimStatus { AWAITING_CLAIM, CLAIMED_COMPLETE, EXPIRED, REFUND_REQUESTED }

data class ItemFulfillmentOutcome(
    val itemId: String,
    val unitIndex: Int,
    val status: DispenseOutcome = DispenseOutcome.PENDING,
    val faultCode: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("itemId", itemId)
        put("unitIndex", unitIndex)
        put("status", status.name)
        put("faultCode", faultCode ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): ItemFulfillmentOutcome = ItemFulfillmentOutcome(
            itemId = json.getString("itemId"),
            unitIndex = json.getInt("unitIndex"),
            status = DispenseOutcome.valueOf(json.getString("status")),
            faultCode = if (json.isNull("faultCode")) null else json.getInt("faultCode")
        )
    }
}

data class Session(
    val phone: String,
    val loginTimeMs: Long = System.currentTimeMillis()
)

data class OrderItem(
    val item_id: String,
    val box: Byte,
    val channel: Byte,
    val qty: Int,
    val price: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("item_id", item_id)
        put("box", box.toInt())
        put("channel", channel.toInt())
        put("qty", qty)
        put("price", price)
    }

    companion object {
        fun fromJson(json: JSONObject): OrderItem = OrderItem(
            item_id = json.getString("item_id"),
            box = json.getInt("box").toByte(),
            channel = json.getInt("channel").toByte(),
            qty = json.getInt("qty"),
            price = json.getDouble("price")
        )
    }
}

data class PaymentInfo(
    val status: PaymentStatus,
    val amount: Double,
    val provider: String,
    val provider_ref: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", status.name)
        put("amount", amount)
        put("provider", provider)
        put("provider_ref", provider_ref ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): PaymentInfo = PaymentInfo(
            status = PaymentStatus.valueOf(json.getString("status")),
            amount = json.getDouble("amount"),
            provider = json.getString("provider"),
            provider_ref = if (json.isNull("provider_ref")) null else json.getString("provider_ref")
        )
    }
}

data class FulfillmentInfo(
    val state: FulfillmentState,
    val fault_code: Int? = null,
    val itemOutcomes: List<ItemFulfillmentOutcome> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("state", state.name)
        put("fault_code", fault_code ?: JSONObject.NULL)
        put("itemOutcomes", itemOutcomes.fold(JSONArray()) { acc, outcome -> acc.put(outcome.toJson()) })
    }

    companion object {
        fun fromJson(json: JSONObject): FulfillmentInfo {
            val array = if (json.has("itemOutcomes")) json.getJSONArray("itemOutcomes") else JSONArray()
            val list = mutableListOf<ItemFulfillmentOutcome>()
            for (i in 0 until array.length()) {
                list.add(ItemFulfillmentOutcome.fromJson(array.getJSONObject(i)))
            }
            return FulfillmentInfo(
                state = FulfillmentState.valueOf(json.getString("state")),
                fault_code = if (json.isNull("fault_code")) null else json.getInt("fault_code"),
                itemOutcomes = list
            )
        }
    }
}

data class TransactionRecord(
    val transaction_id: String,
    val time: Long,
    val sync_status: SyncStatus,
    val order: List<OrderItem>,
    val payment: PaymentInfo,
    val fulfillment: FulfillmentInfo,
    val phone: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("transaction_id", transaction_id)
        put("time", time)
        put("sync_status", sync_status.name)
        put("order", order.fold(JSONArray()) { acc, item -> acc.put(item.toJson()) })
        put("payment", payment.toJson())
        put("fulfillment", fulfillment.toJson())
        put("phone", phone ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): TransactionRecord {
            val orderArray = json.getJSONArray("order")
            val orderList = mutableListOf<OrderItem>()
            for (i in 0 until orderArray.length()) {
                orderList.add(OrderItem.fromJson(orderArray.getJSONObject(i)))
            }
            return TransactionRecord(
                transaction_id = json.getString("transaction_id"),
                time = json.getLong("time"),
                sync_status = SyncStatus.valueOf(json.getString("sync_status")),
                order = orderList,
                payment = PaymentInfo.fromJson(json.getJSONObject("payment")),
                fulfillment = FulfillmentInfo.fromJson(json.getJSONObject("fulfillment")),
                phone = if (json.isNull("phone")) null else json.getString("phone")
            )
        }
    }
}

data class PendingClaimRecord(
    val claimId: String,
    val transactionId: String,
    val paymentRef: String?,
    val undispensedItemsJson: String,
    val reservedAtMillis: Long,
    val expiresAtMillis: Long,
    val status: PendingClaimStatus,
    val phone: String?
)