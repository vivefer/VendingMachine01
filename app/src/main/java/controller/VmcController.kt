package controller

import config.SlotConfig
import config.SlotConfigRepository
import driver102.Command
import driver102.PollStatusResponse
import driver102.SerialPortManager
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class VmcController(
    private val serialManager: SerialPortManager,
    private val configRepository: SlotConfigRepository
) : IVmcController {

    private val executor = Executors.newSingleThreadExecutor()
    private val dispenseQueue = LinkedBlockingQueue<DispenseTask>()
    @Volatile
    private var isProcessingQueue = false

    private data class DispenseTask(
        val slot: SlotConfig,
        val callback: IVmcController.ResultCallback<String>
    )

    override fun dispenseSlot(slotId: String, callback: IVmcController.ResultCallback<String>) {
        val slot = configRepository.getSlots().firstOrNull { it.slotId == slotId }
        if (slot == null) {
            callback.onError("Slot $slotId not configured.")
            return
        }

        dispenseQueue.offer(DispenseTask(slot, callback))
        processNextTask()
    }

    @Synchronized
    private fun processNextTask() {
        if (isProcessingQueue || dispenseQueue.isEmpty()) return

        isProcessingQueue = true
        executor.execute {
            val task = dispenseQueue.poll()
            if (task != null) {
                executeDispenseLifecycle(task)
            }
            isProcessingQueue = false
            processNextTask()
        }
    }

    private fun executeDispenseLifecycle(task: DispenseTask) {
        val slot = task.slot
        val callback = task.callback

        try {
            val startFrame = Command.startPoll(
                slot.cardAddress,
                slot.motorIndex,
                slot.motorType,
                slot.lightCurtainMode
            )
            serialManager.sendBytes(startFrame)
            Thread.sleep(50)

            val maxWaitMs = 6_000L
            val startTime = System.currentTimeMillis()
            var isRunning = true
            var finalResponse: PollStatusResponse? = null

            while (isRunning && (System.currentTimeMillis() - startTime) < maxWaitMs) {
                try {
                    val queryFrame = Command.queryPollStatus(slot.cardAddress)
                    val responseBytes = serialManager.sendAndReceive(queryFrame, 150)
                    val statusResponse = PollStatusResponse(responseBytes)
                    finalResponse = statusResponse

                    if (statusResponse.status == 2 || (statusResponse.status == 0 && (System.currentTimeMillis() - startTime) > 500)) {
                        isRunning = false
                    } else {
                        Thread.sleep(50)
                    }
                } catch (_: Exception) {
                    Thread.sleep(50)
                }
            }

            if (finalResponse != null) {
                if (finalResponse.errorCode == 0) {
                    val dropText = if (finalResponse.lightCurtainMs > 0) "${finalResponse.lightCurtainMs}ms" else "None"
                    callback.onSuccess("Dispense finished for ${slot.slotId} | Drop: $dropText")
                } else {
                    callback.onError("Dispense error for ${slot.slotId}: ${mapErrorCode(finalResponse.errorCode)}")
                }
            } else {
                callback.onError("Dispense timed out waiting for ${slot.slotId} completion.")
            }
        } catch (e: Exception) {
            callback.onError("Dispense failed: ${e.message}")
        }
    }

    override fun pollStatus(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        executor.execute {
            try {
                val frame = Command.queryPollStatus(boxAddress)
                val responseBytes = serialManager.sendAndReceive(frame, 500)
                val status = PollStatusResponse(responseBytes)
                val dropText = if (status.lightCurtainMs > 0) "${status.lightCurtainMs}ms" else "None"
                val message = "Box: ${status.box} | Motor: ${status.motorNum} | Status: ${status.status} | Fault: ${mapErrorCode(status.errorCode)} | Drop: $dropText"
                callback.onSuccess(message)
            } catch (e: Exception) {
                callback.onError("Poll failed: ${e.message}")
            }
        }
    }

    override fun readTemperature(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        executor.execute {
            try {
                val frame = Command.readTemperature(boxAddress)
                val responseBytes = serialManager.sendAndReceive(frame, 500)
                val temp = responseBytes[2].toInt()
                callback.onSuccess("Temperature: $temp °C")
            } catch (e: Exception) {
                callback.onError("Read Temp failed: ${e.message}")
            }
        }
    }

    override fun readDI(boxAddress: Byte, callback: IVmcController.ResultCallback<String>) {
        executor.execute {
            try {
                val frame = Command.readDI(boxAddress)
                val responseBytes = serialManager.sendAndReceive(frame, 500)
                callback.onSuccess("DI State Hex: ${bytesToHex(responseBytes)}")
            } catch (e: Exception) {
                callback.onError("Read DI failed: ${e.message}")
            }
        }
    }

    override fun writeDO(boxAddress: Byte, channel: Byte, state: Byte, callback: IVmcController.ResultCallback<String>) {
        executor.execute {
            try {
                val frame = Command.writeDO(boxAddress, channel, state)
                serialManager.sendBytes(frame)
                callback.onSuccess("Wrote DO Channel $channel State $state")
            } catch (e: Exception) {
                callback.onError("Write DO failed: ${e.message}")
            }
        }
    }

    private fun mapErrorCode(code: Int): String {
        return when (code) {
            0 -> "Success / Normal"
            1 -> "Overcurrent"
            2 -> "Undercurrent"
            3 -> "Timeout"
            4 -> "Light curtain self-test failed"
            5 -> "Feedback lock not opened"
            else -> "Unknown Error ($code)"
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}