package vmappui.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.vmcontroltemplate.R
import config.AppConfig
import config.SlotConfigRepository
import controller.IVmcController
import controller.VmcControllerFactory
import driver102.SerialPortManager
import vmappui.model.ClaimManager
import vmappui.model.DispenseOutcome
import vmappui.model.FulfillmentInfo
import vmappui.model.FulfillmentState
import vmappui.model.ItemFulfillmentOutcome
import vmappui.model.OrderItem
import vmappui.model.PaymentInfo
import vmappui.model.PaymentProvider
import vmappui.model.PaymentProviderFactory
import vmappui.model.Session
import vmappui.model.SyncStatus
import vmappui.model.TransactionRecord
import vmappui.model.TransactionRepository
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {

    private lateinit var configRepository: SlotConfigRepository
    private var serialManager: SerialPortManager? = null
    private lateinit var vmcController: IVmcController
    private lateinit var paymentProvider: PaymentProvider
    private lateinit var claimManager: ClaimManager

    private lateinit var containerOrderItems: LinearLayout
    private lateinit var txtUserPhone: TextView
    private lateinit var txtCheckoutTotal: TextView
    private lateinit var txtTransactionId: TextView
    private lateinit var txtDispenseStatus: TextView
    private lateinit var btnSimulatePayment: Button
    private lateinit var btnDone: Button
    private lateinit var progressDispense: ProgressBar

    private val orderItems = mutableListOf<OrderItem>()
    private var totalAmount = 0.0
    private var phone: String = ""
    private var currentTransactionId: String = ""
    private lateinit var currentPaymentInfo: PaymentInfo

    private class DispenseTask(
        val slotId: String,
        val unitIndex: Int
    )

    private val dispenseQueue = mutableListOf<DispenseTask>()
    private val itemOutcomes = mutableListOf<ItemFulfillmentOutcome>()

    private val autoLogoutHandler = Handler(Looper.getMainLooper())
    private val AUTO_LOGOUT_DELAY_MS = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        configRepository = SlotConfigRepository(this)
        serialManager = SerialPortManager(this)
        vmcController = VmcControllerFactory.create(serialManager!!, configRepository)
        paymentProvider = PaymentProviderFactory.create()
        claimManager = ClaimManager(this)

        containerOrderItems = findViewById(R.id.containerOrderItems)
        txtUserPhone = findViewById(R.id.txtUserPhone)
        txtCheckoutTotal = findViewById(R.id.txtCheckoutTotal)
        txtTransactionId = findViewById(R.id.txtTransactionId)
        txtDispenseStatus = findViewById(R.id.txtDispenseStatus)
        btnSimulatePayment = findViewById(R.id.btnSimulatePayment)
        btnDone = findViewById(R.id.btnDone)
        progressDispense = findViewById(R.id.progressDispense)

        txtUserPhone.text = "Customer Phone: Not provided"

        parseIntentData()
        renderOrderSummary()

        btnSimulatePayment.setOnClickListener {
            promptPhoneAndPay()
        }

        btnDone.setOnClickListener {
            autoLogoutHandler.removeCallbacksAndMessages(null)
            navigateToCatalog()
        }

        if (!AppConfig.USE_MOCK_HARDWARE) {
            serialManager?.discoverAndConnect()
        }
    }

    private fun parseIntentData() {
        val slotIds = intent.getStringArrayListExtra("EXTRA_SLOT_IDS") ?: arrayListOf()
        val qtys = intent.getIntegerArrayListExtra("EXTRA_QTYS") ?: arrayListOf()

        val allSlots = configRepository.getSlots()
        totalAmount = 0.0
        orderItems.clear()

        for (i in slotIds.indices) {
            val slotId = slotIds[i]
            val qty = qtys.getOrNull(i) ?: 0
            val slot = allSlots.find { it.slotId == slotId }

            if (slot != null && qty > 0) {
                val itemPrice = slot.price
                totalAmount += itemPrice * qty
                orderItems.add(
                    OrderItem(
                        item_id = slot.slotId,
                        box = slot.cardAddress,
                        channel = slot.motorIndex,
                        qty = qty,
                        price = itemPrice
                    )
                )
            }
        }
    }

    private fun renderOrderSummary() {
        containerOrderItems.removeAllViews()
        for (item in orderItems) {
            val row = TextView(this).apply {
                text = "${item.item_id} (Box ${item.box}, Motor ${item.channel}) x${item.qty} - ₹${String.format("%.2f", item.price * item.qty)}"
                textSize = 16f
                setPadding(0, 8, 0, 8)
            }
            containerOrderItems.addView(row)
        }
        txtCheckoutTotal.text = String.format("Total Due: ₹%.2f", totalAmount)
    }

    private fun processPaymentAndDispense() {
        btnSimulatePayment.isEnabled = false
        progressDispense.visibility = View.VISIBLE
        txtDispenseStatus.text = "Processing Payment..."

        paymentProvider.processPayment(totalAmount, object : PaymentProvider.PaymentCallback {
            override fun onSuccess(paymentInfo: PaymentInfo) {
                currentPaymentInfo = paymentInfo
                currentTransactionId = UUID.randomUUID().toString()

                txtTransactionId.text = "Transaction ID: $currentTransactionId"

                dispenseQueue.clear()
                itemOutcomes.clear()

                for (item in orderItems) {
                    for (i in 0 until item.qty) {
                        dispenseQueue.add(DispenseTask(item.item_id, i))
                        itemOutcomes.add(ItemFulfillmentOutcome(item.item_id, i, DispenseOutcome.PENDING))
                    }
                }

                val record = TransactionRecord(
                    transaction_id = currentTransactionId,
                    time = System.currentTimeMillis(),
                    sync_status = SyncStatus.PENDING,
                    order = orderItems,
                    payment = paymentInfo,
                    fulfillment = FulfillmentInfo(state = FulfillmentState.IN_PROGRESS, itemOutcomes = itemOutcomes),
                    phone = phone.ifEmpty { null }
                )
                TransactionRepository(this@CheckoutActivity).saveTransaction(record)

                txtDispenseStatus.text = "Payment Received!\nStarting Dispense..."
                executeNextDispenseStep(0)
            }

            override fun onError(error: String) {
                progressDispense.visibility = View.GONE
                btnSimulatePayment.isEnabled = true
                txtDispenseStatus.text = "Payment Error: $error"
            }
        })
    }

    private fun executeNextDispenseStep(stepIndex: Int) {
        if (stepIndex >= dispenseQueue.size) {
            finalizeTransaction()
            return
        }

        val task = dispenseQueue[stepIndex]
        txtDispenseStatus.text = "Dispensing unit ${stepIndex + 1} of ${dispenseQueue.size} (${task.slotId})..."

        vmcController.dispenseSlot(task.slotId, object : IVmcController.ResultCallback<String> {
            override fun onSuccess(data: String) {
                runOnUiThread {
                    decrementStock(task.slotId, 1)
                    itemOutcomes[stepIndex] = ItemFulfillmentOutcome(task.slotId, task.unitIndex, DispenseOutcome.SUCCESS)
                    updateTransactionProgress()
                    executeNextDispenseStep(stepIndex + 1)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    txtDispenseStatus.text = "Dispense failed on ${task.slotId}: $error"
                    itemOutcomes[stepIndex] = ItemFulfillmentOutcome(task.slotId, task.unitIndex, DispenseOutcome.FAILED, -1)
                    updateTransactionProgress()
                    finalizeTransaction()
                }
            }
        })
    }

    private fun updateTransactionProgress() {
        val record = TransactionRecord(
            transaction_id = currentTransactionId,
            time = System.currentTimeMillis(),
            sync_status = SyncStatus.PENDING,
            order = orderItems,
            payment = currentPaymentInfo,
            fulfillment = FulfillmentInfo(state = FulfillmentState.IN_PROGRESS, itemOutcomes = itemOutcomes),
            phone = phone.ifEmpty { null }
        )
        TransactionRepository(this).saveTransaction(record)
    }

    private fun finalizeTransaction() {
        progressDispense.visibility = View.GONE
        btnDone.text = "Return to Catalog Now"
        btnDone.visibility = View.VISIBLE

        val allSuccess = itemOutcomes.all { it.status == DispenseOutcome.SUCCESS }
        val finalState = if (allSuccess) FulfillmentState.DONE else FulfillmentState.PARTIAL

        val record = TransactionRecord(
            transaction_id = currentTransactionId,
            time = System.currentTimeMillis(),
            sync_status = SyncStatus.PENDING,
            order = orderItems,
            payment = currentPaymentInfo,
            fulfillment = FulfillmentInfo(state = finalState, itemOutcomes = itemOutcomes),
            phone = phone.ifEmpty { null }
        )

        TransactionRepository(this).saveTransaction(record)

        if (finalState == FulfillmentState.PARTIAL) {
            claimManager.createPendingClaimForTransaction(record)
            txtDispenseStatus.text = "Order incomplete! Unclaimed items reserved."
        } else {
            txtDispenseStatus.text = "Dispense Complete! Thank you for your purchase."
            autoLogoutHandler.postDelayed({
                navigateToCatalog()
            }, AUTO_LOGOUT_DELAY_MS)
        }
    }

    private fun decrementStock(slotId: String, qty: Int) {
        val slots = configRepository.getSlots().toMutableList()
        val index = slots.indexOfFirst { it.slotId == slotId }
        if (index >= 0) {
            val updatedSlot = slots[index].copy(stock = (slots[index].stock - qty).coerceAtLeast(0))
            slots[index] = updatedSlot
            configRepository.saveSlots(slots)
        }
    }

    private fun promptPhoneAndPay() {
        val input = EditText(this).apply {
            hint = "Enter phone number"
            inputType = InputType.TYPE_CLASS_PHONE
        }

        AlertDialog.Builder(this)
            .setTitle("Enter Phone Number")
            .setMessage("Please enter your phone number to proceed with payment.")
            .setView(input)
            .setPositiveButton("Proceed") { dialog, _ ->
                val enteredPhone = input.text.toString().trim()
                if (enteredPhone.isEmpty()) {
                    txtDispenseStatus.text = "Please enter a valid phone number"
                    dialog.dismiss()
                    return@setPositiveButton
                }

                phone = enteredPhone
                val session = Session(phone = phone)
                Log.d("CheckoutActivity", "Session created successfully: Phone=${session.phone}")

                txtUserPhone.text = "Customer Phone: $phone"
                processPaymentAndDispense()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToCatalog() {
        val intent = Intent(this, CatalogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoLogoutHandler.removeCallbacksAndMessages(null)
        if (!AppConfig.USE_MOCK_HARDWARE) {
            serialManager?.unregisterReceiver()
            serialManager?.disconnect()
        }
    }
}