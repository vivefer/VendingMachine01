package vmappui.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingClaimDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertClaim(claim: PendingClaimEntity)

    @Update
    fun updateClaim(claim: PendingClaimEntity)

    @Query("SELECT * FROM pending_claims WHERE transactionId = :txId LIMIT 1")
    fun getClaimByTransactionId(txId: String): PendingClaimEntity?

    @Query("SELECT * FROM pending_claims WHERE status = :status")
    fun getClaimsByStatus(status: String): List<PendingClaimEntity>

    @Query("SELECT * FROM pending_claims")
    fun getAllClaims(): List<PendingClaimEntity>
}