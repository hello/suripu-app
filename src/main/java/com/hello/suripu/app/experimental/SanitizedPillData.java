package com.hello.suripu.app.experimental;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jnorgan on 2/28/17.
 */
public class SanitizedPillData {
  @JsonProperty("timestamp")
  public final long timestamp;

  @JsonProperty("timezone_offset")
  public final int offsetMillis;

  @JsonProperty("on_duration_seconds")
  public final Long onDurationInSeconds;

  private SanitizedPillData(final long timestamp,
                            final int offsetMillis,
                            final Long onDurationInSeconds) {
    this.timestamp = timestamp;
    this.offsetMillis = offsetMillis;
    this.onDurationInSeconds = onDurationInSeconds;
  }

  public static class Builder{

    private long timestamp;
    private int offsetMillis;
    private Long onDurationInSeconds = 0L;

    public Builder(){

    }

    public Builder withTimestamp(final long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder withOffsetMillis(final int offsetMillis) {
      this.offsetMillis = offsetMillis;
      return this;
    }

    public Builder withOnDurationInSeconds(final Long onDurationInSeconds) {
      this.onDurationInSeconds = onDurationInSeconds;
      return this;
    }

    public SanitizedPillData build() {
      return new SanitizedPillData(timestamp, offsetMillis, onDurationInSeconds);
    }
  }
}
