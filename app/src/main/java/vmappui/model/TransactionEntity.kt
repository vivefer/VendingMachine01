package vmappui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val transactionId: String,
    val time: Long,
    val syncStatus: String,
    val orderJson: String,
    val paymentJson: String,
    val fulfillmentJson: String,
    val phone: String? = null
)