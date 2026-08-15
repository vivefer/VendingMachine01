package driver102;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SerialPortManager {

    private static final String TAG = "SerialPortManager";
    private static final String ACTION_USB_PERMISSION = "driver102.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;

    private UsbSerialPort activePort;
    private final AtomicBoolean isProbing = new AtomicBoolean(false);

    public SerialPortManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        registerUsbReceiver();
    }

    public synchronized void discoverAndConnect() {
        if (isProbing.get() || activePort != null) return;

        isProbing.set(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                UsbSerialPort verifiedPort = Usb102Probe.findAndVerify102Board(usbManager);

                if (verifiedPort != null) {
                    activePort = verifiedPort;
                    Log.i(TAG, "Connected and verified 102 Board!");
                } else {
                    requestPermissionsIfUngranted();
                }
            } finally {
                isProbing.set(false);
            }
        });
    }

    public synchronized void sendBytes(byte[] data) throws IOException {
        if (activePort == null) {
            throw new IOException("102 Board is not connected or verified.");
        }
        activePort.write(data, 1000);
    }

    public synchronized void disconnect() {
        if (activePort != null) {
            try {
                activePort.close();
            } catch (IOException ignored) {}
            activePort = null;
            Log.i(TAG, "Disconnected 102 Board.");
        }
    }

    public boolean isConnected() {
        return activePort != null;
    }

    private void requestPermissionsIfUngranted() {
        if (usbManager == null) return;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (!usbManager.hasPermission(device)) {
                int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0;

                PendingIntent pi = PendingIntent.getBroadcast(
                        context, 0, new Intent(ACTION_USB_PERMISSION), flags);
                usbManager.requestPermission(device, pi);
                break;
            }
        }
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);

        context.registerReceiver(usbReceiver, filter);
    }

    public void unregisterReceiver() {
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException ignored) {}
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB Device Attached. Probing...");
                discoverAndConnect();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (activePort != null && activePort.getDriver().getDevice().equals(device)) {
                    Log.w(TAG, "102 Board USB cable unplugged!");
                    disconnect();
                }
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    discoverAndConnect();
                }
            }
        }
    };
}