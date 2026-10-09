package controller

import config.AppConfig
import config.SlotConfigRepository
import driver102.SerialPortManager

object VmcControllerFactory {

    fun create(
        serialManager: SerialPortManager,
        configRepository: SlotConfigRepository
    ): IVmcController {
        return if (AppConfig.USE_MOCK_HARDWARE) {
            MockVmcController(configRepository)
        } else {
            VmcController(serialManager, configRepository)
        }
    }
}