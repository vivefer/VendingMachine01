package driver102;

public class Command {
    public static final byte CMD_QUERY_STATUS = 0x03;
    public static final byte CMD_READ_TEMP    = 0x07;
    public static final byte CMD_READ_DI      = 0x09;
    public static final byte CMD_START_POLL   = 0x02;
    public static final byte CMD_WRITE_DO     = 0x0B;

    private static byte[] buildFrame(byte box, byte cmd, byte[] params) {
        byte[] frame = new byte[20];
        frame[0] = box;
        frame[1] = cmd;

        if (params != null && params.length > 0) {
            int copyLen = Math.min(params.length, 16);
            System.arraycopy(params, 0, frame, 2, copyLen);
        }

        int crc = CRC16Util.calculate(frame, 0, 18);
        frame[18] = (byte) ((crc >> 8) & 0xFF);
        frame[19] = (byte) (crc & 0xFF);

        return frame;
    }

    public static byte[] startPoll(byte box, byte motorNum, byte motorType) {
        byte[] params = new byte[16];
        params[0] = motorNum;
        params[1] = motorType;
        params[2] = 0x00;
        params[3] = 0x00;
        params[4] = 0x00;
        params[5] = 0x00;
        return buildFrame(box, CMD_START_POLL, params);
    }

    public static byte[] queryPollStatus(byte box) {
        return buildFrame(box, CMD_QUERY_STATUS, null);
    }

    public static byte[] readTemperature(byte box) {
        return buildFrame(box, CMD_READ_TEMP, null);
    }

    public static byte[] readDI(byte box) {
        return buildFrame(box, CMD_READ_DI, null);
    }

    public static byte[] writeDO(byte box, byte channel, byte state) {
        byte[] params = new byte[16];
        params[0] = channel;
        params[1] = state;
        return buildFrame(box, CMD_WRITE_DO, params);
    }
    /**
     * Query Poll Status Directive (03H).
     */
    public static byte[] queryPollStatus(byte motor) {
        // [Header (01H), Directive (03H), Z1, Z2 (motor index), CRC_Low, CRC_High]
        byte[] payload = new byte[] { 0x01, 0x03, 0x00, motor };

        // Use your CRC16Util class to calculate CRC
        int crc = CRC16Util.calculateCRC(payload);

        byte[] frame = new byte[6];
        System.arraycopy(payload, 0, frame, 0, 4);
        frame[4] = (byte) (crc & 0xFF);         // CRC Low
        frame[5] = (byte) ((crc >> 8) & 0xFF);  // CRC High

        return frame;
    }

    /**
     * Verifies if the received response has a valid CRC using CRC16Util.
     */
    public static boolean verifyResponseCrc(byte[] response) {
        if (response == null || response.length < 4) {
            return false;
        }

        // Calls your existing CRC16Util class
        return CRC16Util.checkCRC(response);
    }
}