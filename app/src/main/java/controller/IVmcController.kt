package controller

interface IVmcController {

    interface ResultCallback<T> {
        fun onSuccess(data: T)
        fun onError(error: String)
    }

    fun dispenseSlot(slotId: String, callback: ResultCallback<String>)
    fun pollStatus(boxAddress: Byte, callback: ResultCallback<String>)
    fun readTemperature(boxAddress: Byte, callback: ResultCallback<String>)
    fun readDI(boxAddress: Byte, callback: ResultCallback<String>)
    fun writeDO(boxAddress: Byte, channel: Byte, state: Byte, callback: ResultCallback<String>)
}