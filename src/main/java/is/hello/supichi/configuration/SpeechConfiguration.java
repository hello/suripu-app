package is.hello.supichi.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SpeechConfiguration extends Configuration {

    @JsonProperty("s3_bucket")
    private S3Configuration s3Configuration;
    public S3Configuration s3Configuration() { return s3Configuration; }

    @JsonProperty("google_api_host")
    private String googleAPIHost;
    public String googleAPIHost() { return googleAPIHost; }

    @JsonProperty("google_api_port")
    private int googleAPIPort;
    public int googleAPIPort() { return googleAPIPort; }

    @JsonProperty("audio_parameters")
    private AudioConfiguration audioConfiguration;
    public AudioConfiguration audioConfiguration() { return audioConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("watson")
    private WatsonConfiguration watsonConfiguration;
    public WatsonConfiguration watsonConfiguration() { return watsonConfiguration;}

    @Valid
    @NotNull
    @JsonProperty("watson_save_audio")
    private S3AudioConfiguration watsonAudioConfiguration;
    public S3AudioConfiguration watsonAudioConfiguration() { return watsonAudioConfiguration;}

    @Valid
    @NotNull
    @JsonProperty("sense_upload_audio")
    private S3AudioConfiguration senseUploadAudioConfiguration;
    public S3AudioConfiguration senseUploadAudioConfiguration() { return senseUploadAudioConfiguration;}

    @JsonProperty("forecastio")
    private String forecastio = "";
    public String forecastio() {
        return forecastio;
    }

    @JsonProperty("kinesis_producer")
    private KinesisProducerConfiguration kinesisProducerConfiguration;
    public KinesisProducerConfiguration kinesisProducerConfiguration() { return kinesisProducerConfiguration; }
}
