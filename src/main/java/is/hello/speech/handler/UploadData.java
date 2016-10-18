package is.hello.speech.handler;

import is.hello.speech.api.Speech;

/**
 * Created by ksg on 9/8/16
 */
public class UploadData {
    public final int protobufSize;
    public final Speech.SpeechRequest request;
    public final byte[] audioBody;

    public UploadData(final int protobufSize, final Speech.SpeechRequest request, final byte[] audioBody) {
        this.protobufSize = protobufSize;
        this.request = request;
        this.audioBody = audioBody;
    }
}
