package is.hello.supichi.models;

public class VoiceRequest {

    public final String senseId;
    public final Long accountId;
    public final String ipAddress;
    public final String transcript;

    public VoiceRequest(String senseId, Long accountId, String transcript, String ipAddress) {
        this.senseId = senseId;
        this.accountId = accountId;
        this.ipAddress = ipAddress;
        this.transcript = transcript;
    }
}
