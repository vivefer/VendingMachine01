package config

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class SlotConfigRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("slot_configs", Context.MODE_PRIVATE)

    fun getSlots(): List<SlotConfig> {
        val jsonString = prefs.getString("slots_key", null) ?: return getDefaultSlots()
        val list = mutableListOf<SlotConfig>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val lightCurtain = when {
                    obj.has("lightCurtainMode") -> obj.getInt("lightCurtainMode").toByte()
                    obj.has("useLightScreen") -> if (obj.getBoolean("useLightScreen")) 1.toByte() else 0.toByte()
                    else -> 0.toByte()
                }

                val itemName = if (obj.has("itemName")) obj.getString("itemName") else ""
                val price = if (obj.has("price")) obj.getDouble("price") else 0.0
                val stock = if (obj.has("stock")) obj.getInt("stock") else 0
                val reservedStock = if (obj.has("reservedStock")) obj.getInt("reservedStock") else 0

                list.add(
                    SlotConfig(
                        slotId = obj.getString("slotId"),
                        cardAddress = obj.getInt("cardAddress").toByte(),
                        motorIndex = obj.getInt("motorIndex").toByte(),
                        motorType = obj.getInt("motorType").toByte(),
                        lightCurtainMode = lightCurtain,
                        itemName = itemName,
                        price = price,
                        stock = stock,
                        reservedStock = reservedStock
                    )
                )
            }
        } catch (e: Exception) {
            return getDefaultSlots()
        }
        return list
    }

    fun saveSlots(slots: List<SlotConfig>) {
        val array = JSONArray()
        for (s in slots) {
            val obj = JSONObject()
            obj.put("slotId", s.slotId)
            obj.put("cardAddress", s.cardAddress.toInt())
            obj.put("motorIndex", s.motorIndex.toInt())
            obj.put("motorType", s.motorType.toInt())
            obj.put("lightCurtainMode", s.lightCurtainMode.toInt())
            obj.put("useLightScreen", s.lightCurtainMode > 0)
            obj.put("itemName", s.itemName)
            obj.put("price", s.price)
            obj.put("stock", s.stock)
            obj.put("reservedStock", s.reservedStock)
            array.put(obj)
        }
        prefs.edit().putString("slots_key", array.toString()).apply()
    }

    fun adjustReservedStock(slotId: String, delta: Int) {
        val slots = getSlots().toMutableList()
        val index = slots.indexOfFirst { it.slotId == slotId }
        if (index >= 0) {
            val updated = slots[index].copy(
                reservedStock = (slots[index].reservedStock + delta).coerceAtLeast(0)
            )
            slots[index] = updated
            saveSlots(slots)
        }
    }

    fun getDefaultSlots(): List<SlotConfig> {
        return listOf(
            SlotConfig("Slot 1", cardAddress = 1, motorIndex = 0, motorType = 3, lightCurtainMode = 0, itemName = "Item 1", price = 1.50, stock = 10, reservedStock = 0),
            SlotConfig("Slot 2", cardAddress = 1, motorIndex = 1, motorType = 3, lightCurtainMode = 0, itemName = "Item 2", price = 2.00, stock = 10, reservedStock = 0),
            SlotConfig("Slot 3", cardAddress = 1, motorIndex = 2, motorType = 3, lightCurtainMode = 0, itemName = "Item 3", price = 2.50, stock = 10, reservedStock = 0),
            SlotConfig("Slot 4", cardAddress = 1, motorIndex = 3, motorType = 3, lightCurtainMode = 0, itemName = "Item 4", price = 3.00, stock = 10, reservedStock = 0)
        )
    }
}