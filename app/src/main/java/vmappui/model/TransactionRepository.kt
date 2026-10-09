package vmappui.model

import android.content.Context

class TransactionRepository(
    context: Context,
    private val delegate: ITransactionRepository = RoomTransactionRepository(context)
) : ITransactionRepository by delegate