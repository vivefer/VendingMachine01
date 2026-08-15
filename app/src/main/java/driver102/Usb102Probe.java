package driver102;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
//import com.hoho.android.usbserial.driver.C210xSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.ProlificSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public class Usb102Probe {

    private static final String TAG = "Usb102Probe";
    private static final int BAUD_RATE = 9600;
    private static final int READ_TIMEOUT_MS = 300;
    private static final int MAX_RETRIES = 3;

    public static UsbSerialPort findAndVerify102Board(UsbManager usbManager) {
        if (usbManager == null) {
            Log.e(TAG, "UsbManager is null!");
            return null;
        }

        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.i(TAG, "=== USB DEVICE SCAN START | Found " + deviceList.size() + " device(s) ===");

        // Build explicit custom probe table to override Android 7 kernel CH34x driver interference
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x1A86, 0x7523, Ch34xSerialDriver.class); // CH340
        customTable.addProduct(0x1A86, 0x5523, Ch34xSerialDriver.class); // CH341
       // customTable.addProduct(0x10C4, 0xEA60, C210xSerialDriver.class); // CP2102
        customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver.class);  // FT232R
        customTable.addProduct(0x067B, 0x2303, ProlificSerialDriver.class); // PL2303

        UsbSerialProber prober = new UsbSerialProber(customTable);

        for (UsbDevice device : deviceList.values()) {
            String hexVid = String.format("0x%04X", device.getVendorId());
            String hexPid = String.format("0x%04X", device.getProductId());
            Log.i(TAG, "Detected Device -> Name: " + device.getDeviceName()
                    + " | VID: " + hexVid + " | PID: " + hexPid
                    + " | InterfaceCount: " + device.getInterfaceCount());

            UsbSerialDriver driver = prober.probeDevice(device);
            if (driver == null) {
                // Fallback to default library prober
                driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            }

            if (driver == null) {
                Log.w(TAG, "  └─> No compatible UsbSerialDriver found for " + hexVid + ":" + hexPid);
                continue;
            }

            Log.i(TAG, "  └─> Matched Driver: " + driver.getClass().getSimpleName()
                    + " with " + driver.getPorts().size() + " port(s).");

            for (UsbSerialPort port : driver.getPorts()) {
                if (probePort(usbManager, port)) {
                    Log.i(TAG, "SUCCESS: 102 Vending Board verified on port!");
                    return port;
                }
            }
        }

        Log.w(TAG, "=== USB DEVICE SCAN END | 102 Board NOT found ===");
        return null;
    }

    private static boolean probePort(UsbManager usbManager, UsbSerialPort port) {
        UsbDeviceConnection connection = usbManager.openDevice(port.getDriver().getDevice());
        if (connection == null) {
            Log.e(TAG, "Unable to open UsbDeviceConnection (Permission missing or claimed by OS).");
            return false;
        }

        try {
            port.open(connection);

            // Force interface claiming to override Android 7 kernel CH34x driver
            try {
                connection.claimInterface(port.getDriver().getDevice().getInterface(0), true);
            } catch (Exception e) {
                Log.w(TAG, "Explicit claimInterface warning: " + e.getMessage());
            }

            port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            try {
                port.setDTR(true);
                port.setRTS(true);
            } catch (IOException e) {
                Log.w(TAG, "Failed to set DTR/RTS: " + e.getMessage());
            }

            // Brief delay to allow serial lines to settle after opening connection
            Thread.sleep(50);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                // Flush stale RX buffer
                byte[] flushBuf = new byte[256];
                port.read(flushBuf, 30);

                // Use Address 0x00 matching python script
                byte[] queryPacket = Command.queryPollStatus((byte) 0x01);

                // Log outgoing HEX bytes
                Log.d(TAG, "[Attempt " + attempt + "] Sending Hex TX: " + bytesToHex(queryPacket));

                port.write(queryPacket, READ_TIMEOUT_MS);
                Log.d(TAG, "[Attempt " + attempt + "] Successfully wrote query packet to TX line.");
                // Accumulate RX response bytes
                byte[] fullBuffer = new byte[64];
                int totalBytesRead = 0;
                long startTime = System.currentTimeMillis();

                while (totalBytesRead < 20 && (System.currentTimeMillis() - startTime) < READ_TIMEOUT_MS) {
                    byte[] tempBuf = new byte[32];
                    int len = port.read(tempBuf, 50);
                    if (len > 0) {
                        System.arraycopy(tempBuf, 0, fullBuffer, totalBytesRead, len);
                        totalBytesRead += len;
                    }
                }

                if (totalBytesRead > 0) {
                    byte[] receivedData = Arrays.copyOf(fullBuffer, totalBytesRead);
                    Log.d(TAG, "[Attempt " + attempt + "] Received Hex RX (" + totalBytesRead + " bytes): " + bytesToHex(receivedData));
                } else {
                    Log.d(TAG, "[Attempt " + attempt + "] Received 0 bytes.");
                }

                if (totalBytesRead >= 20) {
                    byte[] actualData = Arrays.copyOf(fullBuffer, 20);
                    if (Command.verifyResponseCrc(actualData)) {
                        return true;
                    } else {
                        Log.w(TAG, "CRC validation failed for response packet!");
                    }
                }

                Thread.sleep(100);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error probing serial port: " + e.getMessage(), e);
        }

        try {
            port.close();
        } catch (IOException ignored) {}

        return false;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}