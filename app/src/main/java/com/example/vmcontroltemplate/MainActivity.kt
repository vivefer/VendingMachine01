package com.example.vmcontroltemplate

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import driver102.Command
import driver102.SerialPortManager
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var serialManager: SerialPortManager? = null
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)

        // Set click handlers for testing motor slots
        findViewById<Button>(R.id.btnSlot1).setOnClickListener { dispenseItem(0) }
        findViewById<Button>(R.id.btnSlot2).setOnClickListener { dispenseItem(1) }
        findViewById<Button>(R.id.btnSlot3).setOnClickListener { dispenseItem(2) }
        findViewById<Button>(R.id.btnSlot4).setOnClickListener { dispenseItem(3) }

        // Initialize USB SerialPortManager using Android context
        serialManager = SerialPortManager(this)

        // Auto-discover and connect/probe the 102 board asynchronously
        serialManager?.discoverAndConnect()
    }

    private fun dispenseItem(motorNumber: Byte) {
        val manager = serialManager
        if (manager == null || !manager.isConnected) {
            updateStatusText(getString(R.string.status_not_connected))
            return
        }

        updateStatusText(getString(R.string.status_sending_command, motorNumber.toInt()))

        // Param 1: Card/Box Address (1)
        // Param 2: Motor Number (1..4)
        // Param 3: Motor Type -> 0 for 3-Wire Motor
        val frame = Command.startPoll(1.toByte(), motorNumber, 3.toByte())

        try {
            manager.sendBytes(frame)
        } catch (e: IOException) {
            updateStatusText(getString(R.string.status_send_error, e.message ?: ""))
        }
    }

    private fun updateStatusText(message: String) {
        runOnUiThread {
            txtStatus.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up USB broadcast receiver and close active serial ports
        serialManager?.unregisterReceiver()
        serialManager?.disconnect()
    }
}