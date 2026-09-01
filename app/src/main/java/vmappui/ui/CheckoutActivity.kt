package vmappui.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vmcontroltemplate.R
import config.SlotConfigRepository
import controller.VmcController
import driver102.SerialPortManager
import vmappui.model.FulfillmentInfo
import vmappui.model.FulfillmentState
import vmappui.model.OrderItem
import vmappui.model.PaymentInfo
import vmappui.model.PaymentStatus
import vmappui.model.SyncStatus
import vmappui.model.TransactionRecord
import vmappui.model.TransactionRepository
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {

    private lateinit var configRepository: SlotConfigRepository
    private var serialManager: SerialPortManager? = null
    private var vmcController: VmcController? = null

    private lateinit var containerOrderItems: LinearLayout
    private lateinit var txtUserPhone: TextView
    private lateinit var txtCheckoutTotal: TextView
    private lateinit var txtDispenseStatus: TextView
    private lateinit var btnSimulatePayment: Button
    private lateinit var btnDone: Button
    private lateinit var progressDispense: ProgressBar

    private val orderItems = mutableListOf<OrderItem>()
    private var totalAmount = 0.0
    private var phone: String = ""

    private class DispenseTask(
        val slotId: String
    )

    private val dispenseQueue = mutableListOf<DispenseTask>()

    private val autoLogoutHandler = Handler(Looper.getMainLooper())
    private val AUTO_LOGOUT_DELAY_MS = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        configRepository = SlotConfigRepository(this)
        serialManager = SerialPortManager(this)
        vmcController = VmcController(serialManager!!, configRepository)

        containerOrderItems = findViewById(R.id.containerOrderItems)
        txtUserPhone = findViewById(R.id.txtUserPhone)
        txtCheckoutTotal = findViewById(R.id.txtCheckoutTotal)
        txtDispenseStatus = findViewById(R.id.txtDispenseStatus)
        btnSimulatePayment = findViewById(R.id.btnSimulatePayment)
        btnDone = findViewById(R.id.btnDone)
        progressDispense = findViewById(R.id.progressDispense)

        phone = intent.getStringExtra("EXTRA_PHONE") ?: "Guest"
        txtUserPhone.text = "Customer Phone: $phone"

        parseIntentData()
        renderOrderSummary()

        btnSimulatePayment.setOnClickListener {
            processPaymentAndDispense()
        }

        btnDone.setOnClickListener {
            autoLogoutHandler.removeCallbacksAndMessages(null)
            navigateToLogin()
        }

        serialManager?.discoverAndConnect()
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
        txtDispenseStatus.text = "Simulating Payment..."

        val paymentInfo = PaymentInfo(
            status = PaymentStatus.RECEIVED,
            amount = totalAmount,
            provider = "MOCK_PAYMENT_GATEWAY",
            provider_ref = "TXN_${UUID.randomUUID().toString().take(8)}"
        )

        dispenseQueue.clear()
        for (item in orderItems) {
            for (i in 0 until item.qty) {
                dispenseQueue.add(DispenseTask(item.item_id))
            }
        }

        txtDispenseStatus.text = "Payment Received! Starting Dispense sequence..."
        executeNextDispenseStep(paymentInfo, 0)
    }

    private fun executeNextDispenseStep(paymentInfo: PaymentInfo, stepIndex: Int) {
        if (stepIndex >= dispenseQueue.size) {
            completeTransaction(paymentInfo, FulfillmentState.DONE, null)
            return
        }

        val task = dispenseQueue[stepIndex]
        txtDispenseStatus.text = "Dispensing unit ${stepIndex + 1} of ${dispenseQueue.size} (Slot: ${task.slotId})..."

        vmcController?.dispenseSlot(task.slotId, object : VmcController.ResultCallback<String> {
            override fun onSuccess(data: String) {
                runOnUiThread {
                    decrementStock(task.slotId, 1)
                    executeNextDispenseStep(paymentInfo, stepIndex + 1)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    txtDispenseStatus.text = "Dispense failed on slot ${task.slotId}: $error"
                    completeTransaction(paymentInfo, FulfillmentState.FAILED, -1)
                }
            }
        })
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

    private fun completeTransaction(payment: PaymentInfo, state: FulfillmentState, faultCode: Int?) {
        progressDispense.visibility = View.GONE
        btnDone.text = "Return to Login Now"
        btnDone.visibility = View.VISIBLE

        val record = TransactionRecord(
            transaction_id = UUID.randomUUID().toString(),
            time = System.currentTimeMillis(),
            sync_status = SyncStatus.PENDING,
            order = orderItems,
            payment = payment,
            fulfillment = FulfillmentInfo(state = state, fault_code = faultCode)
        )

        // Persist transaction record locally for audit/sync
        TransactionRepository(this).saveTransaction(record)

        Log.d("CheckoutActivity", "Transaction Complete JSON: ${record.toJson()}")

        if (state == FulfillmentState.DONE) {
            txtDispenseStatus.text = "Dispense Complete! Thank you for your purchase. Auto-logging out..."
        }

        autoLogoutHandler.postDelayed({
            navigateToLogin()
        }, AUTO_LOGOUT_DELAY_MS)
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoLogoutHandler.removeCallbacksAndMessages(null)
        serialManager?.unregisterReceiver()
        serialManager?.disconnect()
    }
}