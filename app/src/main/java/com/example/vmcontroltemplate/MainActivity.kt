package com.example.vmcontroltemplate

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import driver102.Command
import driver102.PollStatusResponse
import driver102.SerialPortManager

class MainActivity : AppCompatActivity() {

    private var serialManager: SerialPortManager? = null
    private lateinit var txtStatus: TextView

    companion object {
        private const val SERIAL_PATH = "/dev/ttyS1"
        private const val BAUD_RATE = 38400
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)

        // Set click handlers for testing motor slots
        findViewById<Button>(R.id.btnSlot1).setOnClickListener { dispenseItem(1) }
        findViewById<Button>(R.id.btnSlot2).setOnClickListener { dispenseItem(2) }
        findViewById<Button>(R.id.btnSlot3).setOnClickListener { dispenseItem(3) }
        findViewById<Button>(R.id.btnSlot4).setOnClickListener { dispenseItem(4) }

        // Start serial connection (or Mock mode)
        serialManager = SerialPortManager()
        serialManager?.open(SERIAL_PATH, BAUD_RATE, object : SerialPortManager.OnFrameReceivedListener {
            override fun onResponse(resp: PollStatusResponse) {
                val statusStr = when (resp.status) {
                    1 -> "Dispensing..."
                    2 -> "Finished Success!"
                    3 -> "ERROR (Code: ${resp.errorCode})"
                    else -> "Idle"
                }

                txtStatus.text = """
                    Box: ${resp.box}
                    Motor: ${resp.motorNum}
                    Status: $statusStr
                    Peak Current: ${resp.peakCurrentMa} mA
                    Run Time: ${resp.runTimeMs} ms
                """.trimIndent()
            }

            override fun onError(msg: String) {
                txtStatus.text = "Serial Message: $msg"
            }
        })
    }

    private fun dispenseItem(motorNumber: Byte) {
        txtStatus.text = "Sending command to Motor #$motorNumber..."
        // Param 1: Card Address (1)
        // Param 2: Motor Number (1..4)
        // Param 3: Motor Type -> 0 for 3-Wire Motor (change 2 to 0)
        val frame = Command.startPoll(1.toByte(), motorNumber, 0.toByte())
        serialManager?.send(frame)
    }

    override fun onDestroy() {
        super.onDestroy()
        serialManager?.close()
    }
}