package is.hello.speech.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Created by ksg on 8/9/16
 */
public class KinesisProducerConfiguration {

    @Valid
    @NotNull
    @JsonProperty("endpoint")
    private String endpoint;
    public String endpoint() { return this.endpoint; }

    @Valid
    @NotNull
    @JsonProperty("region")
    private String region;
    public String region() { return this.region; }

    @Valid
    @NotNull
    @JsonProperty("max_connections")
    private long maxConnections;
    public long maxConnections() { return this.maxConnections; }


    @Valid
    @NotNull
    @JsonProperty("request_timeout")
    private long requstTimeout;
    public long requstTimeout() { return this.requstTimeout; }

    @Valid
    @NotNull
    @JsonProperty("record_max_buffered_time")
    private long recordMaxBufferedTime;
    public long recordMaxBufferedTime() { return this.recordMaxBufferedTime; }

    @Valid
    @NotNull
    @JsonProperty("queue_size")
    private int queueSize;
    public int queueSize() { return this.queueSize; }

    @Valid
    @NotNull
    @JsonProperty("streams")
    private Map< KinesisStream, String> streams;
    public ImmutableMap<KinesisStream, String> streams() {
        return ImmutableMap.copyOf(streams);
    }

}