package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.speech.v1beta1.RecognitionConfig;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/16/16
 */
public class AudioConfiguration {
    @Valid
    @NotNull
    @JsonProperty("interim_results_preference")
    private Boolean interimResultsPreference;
    public Boolean getInterimResultsPreference() { return interimResultsPreference; }

    @Valid
    @NotNull
    @JsonProperty("encoding")
    private RecognitionConfig.AudioEncoding encoding;
    public RecognitionConfig.AudioEncoding getEncoding() { return encoding; }

    @Valid
    @NotNull
    @JsonProperty("buffer_size")
    private int bufferSize;
    public int getBufferSize() { return bufferSize; }

}
