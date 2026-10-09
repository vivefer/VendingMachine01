package vmappui.model

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromOrderItems(items: List<OrderItem>): String {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    @TypeConverter
    fun toOrderItems(json: String): List<OrderItem> {
        val items = mutableListOf<OrderItem>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            items.add(OrderItem.fromJson(array.getJSONObject(i)))
        }
        return items
    }
}