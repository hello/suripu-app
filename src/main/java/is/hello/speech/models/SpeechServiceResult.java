package is.hello.speech.models;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;


/**
 * Created by ksg on 6/17/16
 */
public class SpeechServiceResult {
    private Optional<String> transcript = Optional.absent();
    private float stability = 0.0f;
    private float confidence = 0.0f;
    private boolean isFinal = false;


    public Optional<String> getTranscript() {
        return transcript;
    }

    public void setTranscript(final Optional<String> transcript) {
        this.transcript = transcript;
    }

    public float getStability() {
        return stability;
    }

    public void setStability(final float stability) {
        this.stability = stability;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(final float confidence) {
        this.confidence = confidence;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(final boolean aFinal) {
        isFinal = aFinal;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SpeechServiceResult.class)
                .add("transcript", (transcript.isPresent()) ? transcript.get() : "n/a")
                .add("stability", stability)
                .add("confidence", confidence)
                .toString();
    }

}
