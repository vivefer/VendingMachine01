package controller

import android.util.Log
import config.SlotConfigRepository
import java.util.concurrent.Executors

class MockVmcController(
    private val configRepository: SlotConfigRepository,
    private val mockDelayMs: Long = 1000L,
    private val shouldFail: Boolean = false
) : IVmcController {

    private val executor = Executors.newSingleThreadExecutor()

    override fun dispenseSlot(slotId: String, callback: IVmcController.ResultCallback<String>) {
        Log.d(TAG, "[MOCK] dispenseSlot called for slotId: $slotId")
        val slot = configRepository.getSlots().firstOrNull { it.slotId == slotId }
        if (slot == null) {
            callback.onError("[MOCK] Slot $slotId not configured.")
            return
        }

        executor.execute {
            try {
                Log.d(TAG, "[MOCK] Simulating motor rotation for slot $slotId...")
                Thread.sleep(mockDelayMs)

                if (shouldFail) {
                    Log.d(TAG, "[MOCK] Simulating dispense failure for slot $slotId")
                    callback.onError("[MOCK] Dispense error for ${slot.slotId}: Overcurrent")
                } else {
                    val dropMs = 120
                    Log.d(TAG, "[MOCK] Simulating successful dispense for slot $slotId")
                    callback.onSuccess("[MOCK] Dispense finished for ${slot.slotId} | Drop: ${dropMs}ms")
                }
            } catch (e: Exception) {
                callback.onError("[MOCK] Dispense failed: ${e.message}")
            }
        }
    }

    override fun pollStatus(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        Log.d(TAG, "[MOCK] pollStatus called for boxAddress: $boxAddress")
        executor.execute {
            Thread.sleep(200)
            callback.onSuccess("[MOCK] Box: $boxAddress | Motor: 1 | Status: 0 | Fault: Success / Normal | Drop: 120ms")
        }
    }

    override fun readTemperature(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        Log.d(TAG, "[MOCK] readTemperature called for boxAddress: $boxAddress")
        executor.execute {
            Thread.sleep(200)
            callback.onSuccess("[MOCK] Temperature: 22 °C")
        }
    }

    override fun readDI(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        Log.d(TAG, "[MOCK] readDI called for boxAddress: $boxAddress")
        executor.execute {
            Thread.sleep(200)
            callback.onSuccess("[MOCK] DI State Hex: 00 00 00 00")
        }
    }

    override fun writeDO(boxAddress: Byte, channel: Byte, state: Byte, callback: IVmcController.ResultCallback<String>) {
        Log.d(TAG, "[MOCK] writeDO called for boxAddress: $boxAddress, channel: $channel, state: $state")
        executor.execute {
            Thread.sleep(200)
            callback.onSuccess("[MOCK] Wrote DO Channel $channel State $state")
        }
    }

    companion object {
        private const val TAG = "MockVmcController"
    }
}