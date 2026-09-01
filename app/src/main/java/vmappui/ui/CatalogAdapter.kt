package vmappui.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vmcontroltemplate.R
import config.SlotConfig

class CatalogAdapter(
    private var slots: List<SlotConfig>,
    private val cartQuantities: MutableMap<String, Int>, // slotId -> qty
    private val onCartUpdated: () -> Unit
) : RecyclerView.Adapter<CatalogAdapter.SlotViewHolder>() {

    class SlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtItemName: TextView = view.findViewById(R.id.txtItemName)
        val txtItemDetails: TextView = view.findViewById(R.id.txtItemDetails)
        val btnMinus: Button = view.findViewById(R.id.btnMinus)
        val txtQty: TextView = view.findViewById(R.id.txtQty)
        val btnPlus: Button = view.findViewById(R.id.btnPlus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        val name = if (slot.itemName.isNotEmpty()) slot.itemName else slot.slotId
        holder.txtItemName.text = name


        holder.txtItemDetails.text = String.format("₹%.2f | Stock: %d", slot.price, slot.stock)

        val currentQty = cartQuantities[slot.slotId] ?: 0
        holder.txtQty.text = currentQty.toString()

        holder.btnMinus.isEnabled = currentQty > 0
        holder.btnPlus.isEnabled = currentQty < slot.stock

        holder.btnMinus.setOnClickListener {
            if (currentQty > 0) {
                val newQty = currentQty - 1
                if (newQty == 0) {
                    cartQuantities.remove(slot.slotId)
                } else {
                    cartQuantities[slot.slotId] = newQty
                }
                notifyItemChanged(position)
                onCartUpdated()
            }
        }

        holder.btnPlus.setOnClickListener {
            if (currentQty < slot.stock) {
                cartQuantities[slot.slotId] = currentQty + 1
                notifyItemChanged(position)
                onCartUpdated()
            }
        }
    }

    override fun getItemCount(): Int = slots.size

    fun updateSlots(newSlots: List<SlotConfig>) {
        slots = newSlots
        // Retain only cart items that are still valid and within stock
        val iterator = cartQuantities.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val matchedSlot = slots.find { it.slotId == entry.key }
            if (matchedSlot == null || matchedSlot.stock == 0) {
                iterator.remove()
            } else if (entry.value > matchedSlot.stock) {
                entry.setValue(matchedSlot.stock)
            }
        }
        notifyDataSetChanged()
        onCartUpdated()
    }
}