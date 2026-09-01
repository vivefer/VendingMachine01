package vmappui.model

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class TransactionRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("transaction_prefs", Context.MODE_PRIVATE)

    fun saveTransaction(record: TransactionRecord) {
        val records = getTransactions().toMutableList()
        records.add(record)
        val jsonArray = JSONArray()
        records.forEach { jsonArray.put(it.toJson()) }
        prefs.edit().putString("KEY_TRANSACTIONS", jsonArray.toString()).apply()
    }

    fun getTransactions(): List<TransactionRecord> {
        val jsonString = prefs.getString("KEY_TRANSACTIONS", null) ?: return emptyList()
        val records = mutableListOf<TransactionRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                records.add(TransactionRecord.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return records
    }
}