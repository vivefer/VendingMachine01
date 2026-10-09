package vmappui.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: TransactionEntity)

    @Update
    fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE transactionId = :id LIMIT 1")
    fun getTransactionById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY time DESC")
    fun getAllTransactions(): List<TransactionEntity>
}