package com.example.vmcontroltemplate

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import admin.AdminDialogFragment
import config.SlotConfigRepository
import controller.VmcController
import driver102.SerialPortManager

class MainActivity : AppCompatActivity() {

    private var serialManager: SerialPortManager? = null
    private lateinit var vmcController: VmcController
    private lateinit var configRepository: SlotConfigRepository
    private lateinit var txtStatus: TextView

    private lateinit var slotButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)

        // Bind main activity slot buttons in order
        slotButtons = listOf(
            findViewById(R.id.btnSlot1),
            findViewById(R.id.btnSlot2),
            findViewById(R.id.btnSlot3),
            findViewById(R.id.btnSlot4)
        )

        // Init low-level driver & high-level controllers
        serialManager = SerialPortManager(this)
        configRepository = SlotConfigRepository(this)
        vmcController = VmcController(serialManager!!, configRepository)

        // Setup initial UI button state
        refreshSlotButtons()

        // Hidden trigger: Long press status text to open Admin Panel
        txtStatus.setOnLongClickListener {
            val adminDialog = AdminDialogFragment(vmcController, configRepository) {
                refreshSlotButtons()
                updateStatusText("Slot configurations updated.")
            }
            adminDialog.show(supportFragmentManager, "admin_panel")
            true
        }

        serialManager?.discoverAndConnect()
    }

    private fun refreshSlotButtons() {
        val configuredSlots = configRepository.getSlots()

        slotButtons.forEachIndexed { index, button ->
            if (index < configuredSlots.size) {
                val slot = configuredSlots[index]
                button.text = slot.slotId
                button.visibility = View.VISIBLE
                button.setOnClickListener { dispense(slot.slotId) }
            } else {
                // Hide extra layout buttons if fewer slots exist in config
                button.visibility = View.GONE
                button.setOnClickListener(null)
            }
        }
    }

    private fun dispense(slotId: String) {
        if (serialManager?.isConnected != true) {
            updateStatusText(getString(R.string.status_not_connected))
            return
        }

        updateStatusText("Triggering $slotId...")
        vmcController.dispenseSlot(slotId, object : VmcController.ResultCallback<String> {
            override fun onSuccess(data: String) {
                updateStatusText(data)
            }

            override fun onError(error: String) {
                updateStatusText(error)
            }
        })
    }

    private fun updateStatusText(message: String) {
        runOnUiThread {
            txtStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serialManager?.unregisterReceiver()
        serialManager?.disconnect()
    }
}