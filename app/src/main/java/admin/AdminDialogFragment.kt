package admin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.example.vmcontroltemplate.R
import config.SlotConfig
import config.SlotConfigRepository
import controller.VmcController

class AdminDialogFragment(
    private val vmcController: VmcController,
    private val configRepo: SlotConfigRepository,
    private val onSlotsUpdated: (() -> Unit)? = null
) : DialogFragment() {

    private lateinit var spinnerSlots: Spinner
    private lateinit var edtSlotId: EditText
    private lateinit var edtItemName: EditText
    private lateinit var edtPrice: EditText
    private lateinit var edtStock: EditText
    private lateinit var spinnerCardAddress: Spinner
    private lateinit var spinnerMotorIndex: Spinner
    private lateinit var spinnerMotorType: Spinner
    private lateinit var spinnerLightCurtain: Spinner
    private lateinit var txtLog: TextView

    private var slotsList = mutableListOf<SlotConfig>()

    private val cardAddressOptions = (1..8).toList()
    private val motorIndexOptions = (0..79).toList()

    private val ADD_NEW_LABEL = "-- Add New Slot --"
    private val MAX_SLOTS_PER_BOARD = 80

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_admin, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeInsets.bottom)
            insets
        }

        spinnerSlots = view.findViewById(R.id.spinnerSlots)
        edtSlotId = view.findViewById(R.id.edtSlotId)
        edtItemName = view.findViewById(R.id.edtItemName)
        edtPrice = view.findViewById(R.id.edtPrice)
        edtStock = view.findViewById(R.id.edtStock)
        spinnerCardAddress = view.findViewById(R.id.spinnerCardAddress)
        spinnerMotorIndex = view.findViewById(R.id.spinnerMotorIndex)
        spinnerMotorType = view.findViewById(R.id.spinnerMotorType)
        spinnerLightCurtain = view.findViewById(R.id.spinnerLightCurtain)
        txtLog = view.findViewById(R.id.txtAdminLog)

        setupDropdowns()
        refreshSlotSpinner()

        val hideKeyboardListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                hideKeyboard()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerCardAddress.onItemSelectedListener = hideKeyboardListener
        spinnerMotorIndex.onItemSelectedListener = hideKeyboardListener
        spinnerMotorType.onItemSelectedListener = hideKeyboardListener
        spinnerLightCurtain.onItemSelectedListener = hideKeyboardListener

        spinnerSlots.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                hideKeyboard()
                if (position == 0) {
                    // Selected "-- Add New Slot --"
                    clearFormForNewSlot()
                } else {
                    val actualIndex = position - 1
                    if (actualIndex in slotsList.indices) {
                        val selected = slotsList[actualIndex]
                        edtSlotId.setText(selected.slotId)
                        edtItemName.setText(selected.itemName)
                        edtPrice.setText(selected.price.toString())
                        edtStock.setText(selected.stock.toString())

                        val cardPos = cardAddressOptions.indexOf(selected.cardAddress.toInt()).coerceAtLeast(0)
                        spinnerCardAddress.setSelection(cardPos)

                        val motorPos = motorIndexOptions.indexOf(selected.motorIndex.toInt()).coerceAtLeast(0)
                        spinnerMotorIndex.setSelection(motorPos)

                        val typePos = when (selected.motorType.toInt()) {
                            2 -> 1
                            0 -> 2
                            else -> 0
                        }
                        spinnerMotorType.setSelection(typePos)

                        val curtainPos = selected.lightCurtainMode.toInt().coerceIn(0, 2)
                        spinnerLightCurtain.setSelection(curtainPos)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        view.findViewById<Button>(R.id.btnSaveSlot).setOnClickListener {
            hideKeyboard()
            val newId = edtSlotId.text.toString().trim()
            val itemName = edtItemName.text.toString().trim()
            val price = edtPrice.text.toString().toDoubleOrNull() ?: 0.0
            val stock = edtStock.text.toString().toIntOrNull() ?: 0

            val card = cardAddressOptions[spinnerCardAddress.selectedItemPosition].toByte()
            val motor = motorIndexOptions[spinnerMotorIndex.selectedItemPosition].toByte()
            val type = when (spinnerMotorType.selectedItemPosition) {
                1 -> 2.toByte()
                2 -> 0.toByte()
                else -> 3.toByte()
            }
            val lightCurtainMode = spinnerLightCurtain.selectedItemPosition.toByte()

            if (newId.isEmpty()) {
                txtLog.text = getString(R.string.admin_err_empty_id)
                return@setOnClickListener
            }

            val spinnerPos = spinnerSlots.selectedItemPosition
            val actualIndex = spinnerPos - 1
            val isCreatingNew = actualIndex !in slotsList.indices

            // Duplicate Prevention: Check if the new ID exists on a DIFFERENT slot
            val duplicateIndex = slotsList.indexOfFirst { it.slotId.equals(newId, ignoreCase = true) }
            if (duplicateIndex >= 0 && duplicateIndex != actualIndex) {
                txtLog.text = "Error: Slot ID '$newId' already exists."
                return@setOnClickListener
            }

            // Per-board slot cap validation (max 80 per card address)
            if (isCreatingNew) {
                val slotsOnThisBoard = slotsList.count { it.cardAddress == card }
                if (slotsOnThisBoard >= MAX_SLOTS_PER_BOARD) {
                    txtLog.text = "Maximum of $MAX_SLOTS_PER_BOARD slots reached for Box ${card.toInt()}"
                    return@setOnClickListener
                }
            }

            val newSlot = SlotConfig(
                slotId = newId,
                cardAddress = card,
                motorIndex = motor,
                motorType = type,
                lightCurtainMode = lightCurtainMode,
                itemName = itemName,
                price = price,
                stock = stock
            )

            if (!isCreatingNew) {
                slotsList[actualIndex] = newSlot
            } else {
                slotsList.add(newSlot)
            }

            configRepo.saveSlots(slotsList)
            refreshSlotSpinner()

            // Select newly saved slot in the spinner
            val updatedPosition = slotsList.indexOfFirst { it.slotId == newId }
            if (updatedPosition >= 0) {
                spinnerSlots.setSelection(updatedPosition + 1) // +1 due to ADD_NEW_LABEL at index 0
            }

            txtLog.text = getString(R.string.admin_log_saved, newId, card.toInt(), motor.toInt())
            onSlotsUpdated?.invoke()
        }

        view.findViewById<Button>(R.id.btnDeleteSlot).setOnClickListener {
            hideKeyboard()
            val spinnerPos = spinnerSlots.selectedItemPosition
            val actualIndex = spinnerPos - 1
            if (actualIndex in slotsList.indices) {
                val removed = slotsList.removeAt(actualIndex)
                configRepo.saveSlots(slotsList)
                refreshSlotSpinner()
                txtLog.text = getString(R.string.admin_log_deleted, removed.slotId)
                onSlotsUpdated?.invoke()
            }
        }

        view.findViewById<Button>(R.id.btnTestDispense).setOnClickListener {
            hideKeyboard()
            val spinnerPos = spinnerSlots.selectedItemPosition
            val actualIndex = spinnerPos - 1
            if (actualIndex in slotsList.indices) {
                val selectedSlotId = slotsList[actualIndex].slotId
                vmcController.dispenseSlot(selectedSlotId, createCallback())
            }
        }

        view.findViewById<Button>(R.id.btnPollStatus).setOnClickListener {
            hideKeyboard()
            val card = cardAddressOptions[spinnerCardAddress.selectedItemPosition].toByte()
            vmcController.pollStatus(card, createCallback())
        }

        view.findViewById<Button>(R.id.btnReadTemp).setOnClickListener {
            hideKeyboard()
            val card = cardAddressOptions[spinnerCardAddress.selectedItemPosition].toByte()
            vmcController.readTemperature(card, createCallback())
        }

        view.findViewById<Button>(R.id.btnReadDI).setOnClickListener {
            hideKeyboard()
            val card = cardAddressOptions[spinnerCardAddress.selectedItemPosition].toByte()
            vmcController.readDI(card, createCallback())
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun clearFormForNewSlot() {
        edtSlotId.setText("")
        edtItemName.setText("")
        edtPrice.setText("0.0")
        edtStock.setText("0")
        spinnerCardAddress.setSelection(0)
        spinnerMotorIndex.setSelection(0)
        spinnerMotorType.setSelection(0)
        spinnerLightCurtain.setSelection(0)
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        edtSlotId.clearFocus()
        edtItemName.clearFocus()
        edtPrice.clearFocus()
        edtStock.clearFocus()
    }

    private fun setupDropdowns() {
        val cardAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            cardAddressOptions.map { getString(R.string.admin_box_label, it) }
        )
        cardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCardAddress.adapter = cardAdapter

        val motorAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            motorIndexOptions.map { getString(R.string.admin_motor_label, it) }
        )
        motorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMotorIndex.adapter = motorAdapter

        val motorTypeOptions = listOf(
            getString(R.string.admin_motor_type_3wire),
            getString(R.string.admin_motor_type_2wire),
            getString(R.string.admin_motor_type_solenoid)
        )
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            motorTypeOptions
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMotorType.adapter = typeAdapter

        val lightCurtainOptions = listOf("Disabled (0)", "Ordinary (1)", "Priority (2)")
        val curtainAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            lightCurtainOptions
        )
        curtainAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLightCurtain.adapter = curtainAdapter
    }

    private fun refreshSlotSpinner() {
        slotsList = configRepo.getSlots().toMutableList()
        val options = mutableListOf(ADD_NEW_LABEL)
        options.addAll(slotsList.map { it.slotId })

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            options
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSlots.adapter = adapter
    }

    private fun createCallback() = object : VmcController.ResultCallback<String> {
        override fun onSuccess(data: String) {
            activity?.runOnUiThread { txtLog.text = data }
        }

        override fun onError(error: String) {
            activity?.runOnUiThread { txtLog.text = getString(R.string.admin_log_error, error) }
        }
    }
}