package vmappui.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import admin.AdminDialogFragment
import com.example.vmcontroltemplate.R
import config.SlotConfigRepository
import controller.VmcController
import driver102.SerialPortManager

class CatalogActivity : AppCompatActivity() {

    private lateinit var configRepository: SlotConfigRepository
    private var serialManager: SerialPortManager? = null
    private var vmcController: VmcController? = null

    private lateinit var recyclerCatalog: RecyclerView
    private lateinit var txtCartTotal: TextView
    private lateinit var btnCheckout: Button
    private lateinit var btnAdminAccess: Button

    private val cartQuantities = mutableMapOf<String, Int>() // slotId -> qty
    private lateinit var adapter: CatalogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        configRepository = SlotConfigRepository(this)
        serialManager = SerialPortManager(this)
        vmcController = VmcController(serialManager!!, configRepository)

        recyclerCatalog = findViewById(R.id.recyclerCatalog)
        txtCartTotal = findViewById(R.id.txtCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnAdminAccess = findViewById(R.id.btnAdminAccess)

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

        btnCheckout.setOnClickListener {
            val intent = Intent(this, CheckoutActivity::class.java).apply {
                // Pass cart slot IDs and quantities to checkout phase
                val slotIds = ArrayList(cartQuantities.keys)
                val qtys = ArrayList(cartQuantities.values)
                putExtra("EXTRA_SLOT_IDS", slotIds)
                putExtra("EXTRA_QTYS", qtys)
                putExtra("EXTRA_PHONE", intent.getStringExtra("EXTRA_PHONE"))
                putExtra("EXTRA_LOGIN_TIME", intent.getLongExtra("EXTRA_LOGIN_TIME", 0L))
            }
            startActivity(intent)
        }

        serialManager?.discoverAndConnect()
        updateCartSummary()
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
        adapter.updateSlots(configRepository.getSlots())
    }

    override fun onDestroy() {
        super.onDestroy()
        serialManager?.unregisterReceiver()
        serialManager?.disconnect()
    }
}