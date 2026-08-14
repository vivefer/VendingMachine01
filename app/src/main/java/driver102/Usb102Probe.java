package driver102;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.Arrays;

public class Usb102Probe {

    private static final String TAG = "Usb102Probe";
    private static final int BAUD_RATE = 9600;
    private static final int READ_TIMEOUT_MS = 300;
    private static final int MAX_RETRIES = 3;

    /**
     * Probes all connected USB serial devices to locate and verify the 102 Board.
     */
    public static UsbSerialPort findAndVerify102Board(UsbManager usbManager) {
        if (usbManager == null) return null;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (!isKnownUsbSerialChip(device)) {
                continue; // Skip non-serial hardware like keyboards, touchscreens
            }

            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            if (driver == null) continue;

            for (UsbSerialPort port : driver.getPorts()) {
                if (probePort(usbManager, port)) {
                    Log.i(TAG, "102 Board successfully verified on USB port!");
                    return port;
                }
            }
        }
        Log.w(TAG, "102 Board not found on any connected USB port.");
        return null;
    }

    private static boolean probePort(UsbManager usbManager, UsbSerialPort port) {
        UsbDeviceConnection connection = usbManager.openDevice(port.getDriver().getDevice());
        if (connection == null) {
            Log.e(TAG, "Unable to open USB connection (Permission denied or device busy).");
            return false;
        }

        try {
            port.open(connection);
            port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                // Clear stale buffer data
                byte[] flushBuffer = new byte[256];
                port.read(flushBuffer, 50);

                // Build query packet (03H Directive with default motor index)
                byte[] queryPacket = Command.queryPollStatus((byte) 0x00);
                port.write(queryPacket, READ_TIMEOUT_MS);

                // Read response
                byte[] responseBuffer = new byte[64];
                int bytesRead = port.read(responseBuffer, READ_TIMEOUT_MS);

                if (bytesRead > 0) {
                    byte[] actualData = Arrays.copyOf(responseBuffer, bytesRead);

                    // Verify packet using CRC16Util in driver102 package
                    if (Command.verifyResponseCrc(actualData)) {
                        return true; // Keep port open for usage
                    }
                }

                Thread.sleep(100);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error probing USB serial port: " + e.getMessage());
        }

        // Verification failed, release port
        try {
            port.close();
        } catch (IOException ignored) {}

        return false;
    }

    private static boolean isKnownUsbSerialChip(UsbDevice device) {
        int vid = device.getVendorId();
        return vid == 0x10C4   // Silicon Labs CP210x
                || vid == 0x1A86   // Qinheng CH340 / CH341
                || vid == 0x0403   // FTDI
                || vid == 0x067B;  // Prolific PL2303
    }
}