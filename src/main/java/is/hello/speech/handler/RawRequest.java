package is.hello.speech.handler;

public class RawRequest {

    private final byte[] signedBody;
    private final String senseId;
    private final String ipAddress;

    private RawRequest(byte[] signedBody, String senseId, String ipAddress) {
        this.signedBody = signedBody;
        this.senseId = senseId;
        this.ipAddress = ipAddress;
    }

    public static RawRequest create(byte[] signedBody, String senseId, String ipAddress) {
        return new RawRequest(signedBody, senseId, ipAddress);
    }

    public byte[] signedBody() {
        return signedBody;
    }

    public String senseId() {
        return senseId;
    }

    public String ipAddress() {
        return ipAddress;
    }
}
