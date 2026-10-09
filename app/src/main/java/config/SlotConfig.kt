package config

data class SlotConfig(
    val slotId: String,          // e.g., "Slot 1"
    val cardAddress: Byte = 1,   // Default box 1 (valid range 1..8)
    val motorIndex: Byte,        // 0..N
    val motorType: Byte = 3,     // Default 3 (3-wire)
    val lightCurtainMode: Byte = 0, // 0=Disabled, 1=Ordinary, 2=Curtain check
    val itemName: String = "",
    val price: Double = 0.0,
    val stock: Int = 0,
    val reservedStock: Int = 0   // Reserved stock for pending partial order claims
) {
    val availableStock: Int
        get() = (stock - reservedStock).coerceAtLeast(0)
}