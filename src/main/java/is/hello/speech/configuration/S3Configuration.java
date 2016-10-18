package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Created by ksg on 6/16/16
 */
public class S3Configuration extends Configuration {
    @Valid
    @NotNull
    @JsonProperty("bucket")
    private String bucket;
    public String getBucket() { return bucket; }

    @Valid
    @NotNull
    @JsonProperty("aws_access_key")
    private String awsAccessKey;
    public String getAwsAccessKey() { return awsAccessKey; }

    @Valid
    @NotNull
    @JsonProperty("aws_secret_key")
    private String awsSecretKey;
    public String getAwsSecretKey() { return awsSecretKey; }
}
