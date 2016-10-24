package is.hello.supichi.kinesis;

import com.hello.suripu.core.speech.models.SpeechResult;
import is.hello.supichi.api.SpeechResultsKinesis;

/**
 * Created by ksg on 8/11/16
 */
public class KinesisData {
    public final SpeechResult speechResult;
    public final SpeechResultsKinesis.SpeechResultsData.Action action;
    public final byte [] audioData;


    public KinesisData(final SpeechResult speechResult, final SpeechResultsKinesis.SpeechResultsData.Action  action, final byte[] audioData) {
        this.speechResult = speechResult;
        this.action = action;
        this.audioData = audioData;
    }
}
