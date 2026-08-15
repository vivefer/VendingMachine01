package driver102;

public class Command {
    public static final byte CMD_QUERY_STATUS = 0x03;
    public static final byte CMD_START_POLL   = 0x05; // 0x05 per manual section 8.4 (Motor Run)
    public static final byte CMD_READ_TEMP    = 0x07;
    public static final byte CMD_WRITE_DO     = 0x08; // 0x08 per manual section 8.6
    public static final byte CMD_READ_DI      = 0x09;

    private static byte[] buildFrame(byte box, byte cmd, byte[] params) {
        byte[] frame = new byte[20];
        frame[0] = box;
        frame[1] = cmd;

        if (params != null && params.length > 0) {
            int copyLen = Math.min(params.length, 16);
            System.arraycopy(params, 0, frame, 2, copyLen);
        }

        int crc = CRC16Util.calculate(frame, 0, 18);
        frame[18] = (byte) (crc & 0xFF);         // CRC Low
        frame[19] = (byte) ((crc >> 8) & 0xFF);  // CRC High

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

    public static byte[] queryPollStatus(byte boxAddress) {
        byte[] packet = new byte[20];
        packet[0] = boxAddress; // Address 0x01
        packet[1] = 0x03;       // Opcode 0x03
        // bytes 2 to 17 remain 0x00

        // ATTACH CRC TO BYTES 18 & 19
        CRC16Util.appendCRC(packet);

        return packet;
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

    public static boolean verifyResponseCrc(byte[] response) {
        return CRC16Util.checkCRC(response);
    }
}