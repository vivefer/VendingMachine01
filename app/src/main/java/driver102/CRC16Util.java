package driver102;

public class CRC16Util {

    /**
     * Calculates 16-bit Modbus CRC dynamically (No lookup table needed).
     */
    public static int calculate(byte[] data, int offset, int len) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /**
     * Calculates CRC for the payload (length - 2) and appends
     * Low Byte first, High Byte second.
     */
    public static void appendCRC(byte[] data) {
        if (data == null || data.length < 3) return;

        int payloadLen = data.length - 2;
        int crc = calculate(data, 0, payloadLen);

        data[payloadLen]     = (byte) (crc & 0xFF);        // LSB
        data[payloadLen + 1] = (byte) ((crc >> 8) & 0xFF); // MSB
    }

    /**
     * Validates incoming response bytes against their appended CRC.
     */
    public static boolean checkCRC(byte[] response) {
        if (response == null || response.length < 4) return false;

        int payloadLen = response.length - 2;
        int expectedCrc = calculate(response, 0, payloadLen);

        int actualLowByte  = response[payloadLen] & 0xFF;
        int actualHighByte = response[payloadLen + 1] & 0xFF;
        int actualCrc      = (actualHighByte << 8) | actualLowByte;

        return expectedCrc == actualCrc;
    }
}