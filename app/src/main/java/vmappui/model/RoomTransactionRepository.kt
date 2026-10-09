package vmappui.model

import android.content.Context

class RoomTransactionRepository(context: Context) : ITransactionRepository {

    private val dao = AppDatabase.getInstance(context).transactionDao()

    override fun saveTransaction(record: TransactionRecord) {
        val entity = TransactionEntity(
            transactionId = record.transaction_id,
            time = record.time,
            syncStatus = record.sync_status.name,
            orderJson = Converters().fromOrderItems(record.order),
            paymentJson = record.payment.toJson().toString(),
            fulfillmentJson = record.fulfillment.toJson().toString()
        )
        dao.insertTransaction(entity)
    }

    override fun getTransactions(): List<TransactionRecord> {
        return dao.getAllTransactions().map { entity ->
            TransactionRecord(
                transaction_id = entity.transactionId,
                time = entity.time,
                sync_status = SyncStatus.valueOf(entity.syncStatus),
                order = Converters().toOrderItems(entity.orderJson),
                payment = PaymentInfo.fromJson(org.json.JSONObject(entity.paymentJson)),
                fulfillment = FulfillmentInfo.fromJson(org.json.JSONObject(entity.fulfillmentJson))
            )
        }
    }
}