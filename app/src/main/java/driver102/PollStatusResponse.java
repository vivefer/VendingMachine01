package driver102;

public class PollStatusResponse {
    public final int box;
    public final int status;
    public final int motorNum;
    public final int errorCode;
    public final int peakCurrentMa;
    public final int avgCurrentMa;
    public final int runTimeMs;

    public PollStatusResponse(byte[] frame) {
        if (frame == null || frame.length < 20) {
            throw new IllegalArgumentException("Invalid response length");
        }

        int expectedCrc = CRC16Util.calculate(frame, 0, 18);
        int actualCrc = (frame[18] & 0xFF) | ((frame[19] & 0xFF) << 8); // Low-byte first, High-byte second
        if (expectedCrc != actualCrc) {
            throw new IllegalArgumentException("CRC Mismatch");
        }

        this.box = frame[0] & 0xFF;
        this.status = frame[2] & 0xFF;
        this.motorNum = frame[3] & 0xFF;
        this.errorCode = frame[4] & 0xFF;
        this.peakCurrentMa = ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF);
        this.avgCurrentMa = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);
        this.runTimeMs = ((frame[9] & 0xFF) << 8) | (frame[10] & 0xFF);
    }
}