package vmappui.model

import android.content.Context
import config.SlotConfigRepository
import org.json.JSONArray
import java.util.UUID

class ClaimManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val transactionDao = db.transactionDao()
    private val claimDao = db.pendingClaimDao()
    private val slotRepo = SlotConfigRepository(context)

    fun checkAndCreatePendingClaimsOnStartup() {
        processExpiredClaims()

        val txs = transactionDao.getAllTransactions()
        for (txEntity in txs) {
            val record = TransactionRecord(
                transaction_id = txEntity.transactionId,
                time = txEntity.time,
                sync_status = SyncStatus.valueOf(txEntity.syncStatus),
                order = Converters().toOrderItems(txEntity.orderJson),
                payment = PaymentInfo.fromJson(org.json.JSONObject(txEntity.paymentJson)),
                fulfillment = FulfillmentInfo.fromJson(org.json.JSONObject(txEntity.fulfillmentJson)),
                phone = txEntity.phone
            )

            val state = record.fulfillment.state
            if (state == FulfillmentState.IN_PROGRESS || state == FulfillmentState.PARTIAL) {
                val existingClaim = claimDao.getClaimByTransactionId(record.transaction_id)
                if (existingClaim == null) {
                    createPendingClaimForTransaction(record)
                }
            }
        }
    }

    fun createPendingClaimForTransaction(record: TransactionRecord) {
        val undispensedItems = mutableListOf<String>()
        val outcomes = record.fulfillment.itemOutcomes

        if (outcomes.isNotEmpty()) {
            outcomes.filter { it.status != DispenseOutcome.SUCCESS }.forEach { undispensedItems.add(it.itemId) }
        } else {
            record.order.forEach { item ->
                repeat(item.qty) { undispensedItems.add(item.item_id) }
            }
        }

        if (undispensedItems.isEmpty()) return

        val now = System.currentTimeMillis()
        val claim = PendingClaimEntity(
            claimId = UUID.randomUUID().toString(),
            transactionId = record.transaction_id,
            paymentRef = record.payment.provider_ref,
            undispensedItemsJson = JSONArray(undispensedItems).toString(),
            reservedAtMillis = now,
            expiresAtMillis = now + 24 * 60 * 60 * 1000L,
            status = PendingClaimStatus.AWAITING_CLAIM.name,
            phone = record.phone
        )

        claimDao.insertClaim(claim)

        val itemCounts = undispensedItems.groupingBy { it }.eachCount()
        for ((slotId, count) in itemCounts) {
            slotRepo.adjustReservedStock(slotId, count)
        }
    }

    fun processExpiredClaims() {
        val now = System.currentTimeMillis()
        val awaitingClaims = claimDao.getClaimsByStatus(PendingClaimStatus.AWAITING_CLAIM.name)

        for (claim in awaitingClaims) {
            if (now >= claim.expiresAtMillis) {
                val updated = claim.copy(status = PendingClaimStatus.EXPIRED.name)
                claimDao.updateClaim(updated)

                val items = jsonToList(claim.undispensedItemsJson)
                val itemCounts = items.groupingBy { it }.eachCount()
                for ((slotId, count) in itemCounts) {
                    slotRepo.adjustReservedStock(slotId, -count)
                }
            }
        }
    }

    fun markClaimComplete(txId: String) {
        val claim = claimDao.getClaimByTransactionId(txId) ?: return
        val updated = claim.copy(status = PendingClaimStatus.CLAIMED_COMPLETE.name)
        claimDao.updateClaim(updated)

        val items = jsonToList(claim.undispensedItemsJson)
        val itemCounts = items.groupingBy { it }.eachCount()
        for ((slotId, count) in itemCounts) {
            slotRepo.adjustReservedStock(slotId, -count)
        }
    }

    fun markClaimRefunded(txId: String) {
        val claim = claimDao.getClaimByTransactionId(txId) ?: return
        val updated = claim.copy(status = PendingClaimStatus.REFUND_REQUESTED.name)
        claimDao.updateClaim(updated)
    }

    fun getClaimByTransactionId(txId: String): PendingClaimEntity? {
        return claimDao.getClaimByTransactionId(txId)
    }

    fun getAdminVisibilityClaims(): List<PendingClaimEntity> {
        return claimDao.getAllClaims().filter {
            it.status == PendingClaimStatus.EXPIRED.name || it.status == PendingClaimStatus.REFUND_REQUESTED.name
        }
    }

    private fun jsonToList(json: String): List<String> {
        val list = mutableListOf<String>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }
}