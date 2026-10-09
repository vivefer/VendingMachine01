package vmappui.model

interface ITransactionRepository {
    fun saveTransaction(record: TransactionRecord)
    fun getTransactions(): List<TransactionRecord>
}