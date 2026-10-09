package vmappui.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_claims")
data class PendingClaimEntity(
    @PrimaryKey val claimId: String,
    val transactionId: String,
    val paymentRef: String?,
    val undispensedItemsJson: String,
    val reservedAtMillis: Long,
    val expiresAtMillis: Long,
    val status: String,
    val phone: String? = null
)