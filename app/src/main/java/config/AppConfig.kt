package config

object AppConfig {
    /**
     * Toggle to switch between mock hardware and real serial hardware.
     * Set to true to run against MockVmcController without requiring USB board connection.
     */
    const val USE_MOCK_HARDWARE: Boolean = false
}