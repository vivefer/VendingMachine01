package vmappui.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import admin.AdminDialogFragment
import com.example.vmcontroltemplate.R
import config.AppConfig
import config.SlotConfigRepository
import controller.IVmcController
import controller.VmcControllerFactory
import driver102.SerialPortManager
import org.json.JSONArray
import vmappui.model.ClaimManager
import vmappui.model.PendingClaimEntity
import vmappui.model.PendingClaimStatus

class CatalogActivity : AppCompatActivity() {

    private lateinit var configRepository: SlotConfigRepository
    private var serialManager: SerialPortManager? = null
    private var vmcController: IVmcController? = null
    private lateinit var claimManager: ClaimManager

    private lateinit var recyclerCatalog: RecyclerView
    private lateinit var txtCartTotal: TextView
    private lateinit var btnCheckout: Button
    private lateinit var btnAdminAccess: Button
    private lateinit var btnClaimPartial: Button

    private val cartQuantities = mutableMapOf<String, Int>()
    private lateinit var adapter: CatalogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        configRepository = SlotConfigRepository(this)
        serialManager = SerialPortManager(this)
        vmcController = VmcControllerFactory.create(serialManager!!, configRepository)
        claimManager = ClaimManager(this)

        claimManager.checkAndCreatePendingClaimsOnStartup()

        recyclerCatalog = findViewById(R.id.recyclerCatalog)
        txtCartTotal = findViewById(R.id.txtCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnAdminAccess = findViewById(R.id.btnAdminAccess)
        btnClaimPartial = findViewById(R.id.btnClaimPartial)

        recyclerCatalog.layoutManager = LinearLayoutManager(this)
        adapter = CatalogAdapter(configRepository.getSlots(), cartQuantities) {
            updateCartSummary()
        }
        recyclerCatalog.adapter = adapter

        btnAdminAccess.setOnClickListener {
            val adminDialog = AdminDialogFragment(vmcController!!, configRepository) {
                adapter.updateSlots(configRepository.getSlots())
            }
            adminDialog.show(supportFragmentManager, "admin_panel")
        }

        btnClaimPartial.setOnClickListener {
            showClaimDialog()
        }

        btnCheckout.setOnClickListener {
            val intent = Intent(this, CheckoutActivity::class.java).apply {
                val slotIds = ArrayList(cartQuantities.keys)
                val qtys = ArrayList(cartQuantities.values)
                putExtra("EXTRA_SLOT_IDS", slotIds)
                putExtra("EXTRA_QTYS", qtys)
            }
            startActivity(intent)
        }

        if (!AppConfig.USE_MOCK_HARDWARE) {
            serialManager?.discoverAndConnect()
        }
        updateCartSummary()
    }

    private fun showClaimDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val edtTxId = EditText(this).apply { hint = "Transaction ID" }
        val edtPhone = EditText(this).apply {
            hint = "Phone Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        container.addView(edtTxId)
        container.addView(edtPhone)

        AlertDialog.Builder(this)
            .setTitle("Claim Partial Order")
            .setMessage("Enter your Transaction ID to dispense remaining items.")
            .setView(container)
            .setPositiveButton("Claim & Dispense") { _, _ ->
                val txId = edtTxId.text.toString().trim()
                val phoneInput = edtPhone.text.toString().trim()

                if (txId.isEmpty()) {
                    Toast.makeText(this, "Please enter a Transaction ID", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (phoneInput.isEmpty()) {
                    Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val claim: PendingClaimEntity? = claimManager.getClaimByTransactionId(txId)
                if (claim == null || claim.status != PendingClaimStatus.AWAITING_CLAIM.name) {
                    Toast.makeText(this, "No active claim found for this Transaction ID.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (System.currentTimeMillis() >= claim.expiresAtMillis) {
                    claimManager.processExpiredClaims()
                    Toast.makeText(this, "This claim has expired.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (claim.phone != phoneInput) {
                    Toast.makeText(this, "Phone number mismatch.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                dispenseClaimItems(txId, claim.undispensedItemsJson)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dispenseClaimItems(txId: String, itemsJson: String) {
        val array = JSONArray(itemsJson)
        val itemsList = mutableListOf<String>()
        for (i in 0 until array.length()) {
            itemsList.add(array.getString(i))
        }

        executeClaimDispenseQueue(txId, itemsList, 0)
    }

    private fun executeClaimDispenseQueue(txId: String, itemsList: List<String>, index: Int) {
        if (index >= itemsList.size) {
            claimManager.markClaimComplete(txId)
            adapter.updateSlots(configRepository.getSlots())
            Toast.makeText(this, "Claim successfully dispensed!", Toast.LENGTH_LONG).show()
            return
        }

        val slotId = itemsList[index]
        vmcController?.dispenseSlot(slotId, object : IVmcController.ResultCallback<String> {
            override fun onSuccess(data: String) {
                runOnUiThread {
                    decrementStock(slotId, 1)
                    executeClaimDispenseQueue(txId, itemsList, index + 1)
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@CatalogActivity, "Dispense interrupted on $slotId: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun decrementStock(slotId: String, qty: Int) {
        val slots = configRepository.getSlots().toMutableList()
        val slotIndex = slots.indexOfFirst { it.slotId == slotId }
        if (slotIndex >= 0) {
            val updated = slots[slotIndex].copy(
                stock = (slots[slotIndex].stock - qty).coerceAtLeast(0)
            )
            slots[slotIndex] = updated
            configRepository.saveSlots(slots)
        }
    }

    private fun updateCartSummary() {
        val slots = configRepository.getSlots()
        var total = 0.0
        var totalItemCount = 0

        for ((slotId, qty) in cartQuantities) {
            val slot = slots.find { it.slotId == slotId }
            if (slot != null) {
                total += slot.price * qty
                totalItemCount += qty
            }
        }

        txtCartTotal.text = String.format("Total: ₹%.2f", total)
        btnCheckout.isEnabled = totalItemCount > 0
    }

    override fun onResume() {
        super.onResume()
        claimManager.checkAndCreatePendingClaimsOnStartup()
        adapter.updateSlots(configRepository.getSlots())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!AppConfig.USE_MOCK_HARDWARE) {
            serialManager?.unregisterReceiver()
            serialManager?.disconnect()
        }
    }
}