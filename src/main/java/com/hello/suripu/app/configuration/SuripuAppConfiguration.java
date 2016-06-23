package com.hello.suripu.app.configuration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hello.suripu.coredw8.configuration.GraphiteConfiguration;
import com.hello.suripu.coredw8.configuration.KinesisConfiguration;
import com.hello.suripu.coredw8.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.coredw8.configuration.NewDynamoDBConfiguration;
import com.hello.suripu.coredw8.configuration.PushNotificationsConfiguration;
import com.hello.suripu.coredw8.configuration.S3BucketConfiguration;
import com.hello.suripu.coredw8.configuration.TaimurainHttpClientConfiguration;
import com.hello.suripu.coredw8.configuration.TimelineAlgorithmConfiguration;

import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;

public class SuripuAppConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("common_db")
    private DataSourceFactory commonDB = new DataSourceFactory();
    public DataSourceFactory getCommonDB() {
        return commonDB;
    }

    @Valid
    @NotNull
    @JsonProperty("insights_db")
    private DataSourceFactory insightsDB = new DataSourceFactory();
    public DataSourceFactory getInsightsDB() {
        return insightsDB;
    }

    @Valid
    @NotNull
    @JsonProperty("metrics_enabled")
    private Boolean metricsEnabled;
    public Boolean getMetricsEnabled() {
        return metricsEnabled;
    }

    @Valid
    @JsonProperty("debug")
    private Boolean debug = Boolean.FALSE;

    public Boolean getDebug() {
        return debug;
    }

    @Valid
    @NotNull
    @JsonProperty("graphite")
    private GraphiteConfiguration graphite;
    public GraphiteConfiguration getGraphite() {
        return graphite;
    }

    @Valid
    @NotNull
    @JsonProperty("dynamodb")
    private NewDynamoDBConfiguration dynamoDBConfiguration;
    public NewDynamoDBConfiguration dynamoDBConfiguration(){
        return dynamoDBConfiguration;
    }

    @Valid
    @NotNull
    @JsonProperty("allowed_query_range_seconds")
    private Long allowedQueryRange;
    public Long getAllowedQueryRange() {
        return allowedQueryRange;
    }

    @Valid
    @NotNull
    @JsonProperty("kinesis")
    private KinesisConfiguration kinesisConfiguration;
    public KinesisConfiguration getKinesisConfiguration() {
        return kinesisConfiguration;
    }

    @Valid
    @JsonProperty("score_threshold")
    private int scoreThreshold;
    public int getScoreThreshold() {
        return scoreThreshold;
    }


    @Valid
    @JsonProperty("push_notifications")
    private PushNotificationsConfiguration pushNotificationsConfiguration;
    public PushNotificationsConfiguration getPushNotificationsConfiguration() { return pushNotificationsConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("sleep_score_version")
    private String sleepScoreVersion;
    public String getSleepScoreVersion() {
        return this.sleepScoreVersion;
    }

    @Valid
    @NotNull
    @JsonProperty("sleep_stats_version")
    private String sleepStatsVersion;
    public String getSleepStatsVersion() {
        return this.sleepStatsVersion;
    }

    @Valid
    @NotNull
    @JsonProperty("question_configs")
    private QuestionConfiguration questionConfigs;
    public QuestionConfiguration getQuestionConfigs() {
        return this.questionConfigs;
    }

    @Valid
    @NotNull
    @JsonProperty("email")
    private EmailConfiguration emailConfiguration;
    public EmailConfiguration emailConfiguration() {
        return emailConfiguration;
    }

    @Valid
    @NotNull
    @Min(1)
    @Max(100)
    @JsonProperty("max_cache_refresh_days")
    private int maxCacheRefreshDay;
    public Integer getMaxCacheRefreshDay() {
        return this.maxCacheRefreshDay;
    }


    @Valid
    @JsonProperty("next_flush_sleep")
    private Long nextFlushSleepMillis = 50L;
    public Long getNextFlushSleepMillis() { return nextFlushSleepMillis; }

    @Valid
    @JsonProperty("stop_month")
    private int stopMonth = 2; // set default to feb
    public int getStopMonth() { return this.stopMonth; }

    @Valid
    @NotNull
    @JsonProperty("timeline_model_ensembles")
    private S3BucketConfiguration timelineModelEnsemblesConfiguration;
    public S3BucketConfiguration getTimelineModelEnsemblesConfiguration() { return timelineModelEnsemblesConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("timeline_seed_model")
    private S3BucketConfiguration timelineSeedModelConfiguration;
    public S3BucketConfiguration getTimelineSeedModelConfiguration() { return timelineSeedModelConfiguration; }

    @JsonProperty("provision_key")
    private S3BucketConfiguration provisionKeyConfiguration = S3BucketConfiguration.create("hello-secure", "hello-pvt.pem");
    public S3BucketConfiguration getProvisionKeyConfiguration() { return provisionKeyConfiguration; }

    @Valid
    @NotNull
    @JsonProperty("messeji_http_client")
    private MessejiHttpClientConfiguration messejiHttpClientConfiguration;
    public MessejiHttpClientConfiguration getMessejiHttpClientConfiguration() { return messejiHttpClientConfiguration; }

    @NotNull
    @JsonProperty("taimurain_http_client")
    private TaimurainHttpClientConfiguration taimurainHttpClientConfiguration;
    public TaimurainHttpClientConfiguration getTaimurainHttpClientConfiguration() { return taimurainHttpClientConfiguration; }

    @Valid
    @JsonProperty("sleep_sound_cache_seconds")
    private Integer sleepSoundCacheSeconds = 5;
    public Integer getSleepSoundCacheSeconds() { return sleepSoundCacheSeconds; }

    @Valid
    @JsonProperty("sleep_sound_duration_cache_seconds")
    private Integer sleepSoundDurationCacheSeconds = 5;
    public Integer getSleepSoundDurationCacheSeconds() { return sleepSoundDurationCacheSeconds; }

    @Valid
    @JsonProperty("rate_limiter")
    private RateLimiterConfiguration rateLimiterConfiguration = new RateLimiterConfiguration();
    public RateLimiterConfiguration getRateLimiterConfiguration() { return rateLimiterConfiguration; }

    @Valid
    @JsonProperty("timeline_algorithm_configuration")
    private TimelineAlgorithmConfiguration timelineAlgorithmConfiguration = new TimelineAlgorithmConfiguration();
    public TimelineAlgorithmConfiguration getTimelineAlgorithmConfiguration() {return timelineAlgorithmConfiguration;}

    @Valid
    @JsonProperty("photo_upload")
    private PhotoUploadConfiguration photoUploadConfiguration;
    public PhotoUploadConfiguration photoUploadConfiguration() {
        return photoUploadConfiguration;
    }

    @Valid
    @JsonProperty("alexa_app_ids")
    private Map<String, String> alexaAppIds = Maps.newHashMap();
    public ImmutableMap<String, String> getAlexaAppIds() {
        return ImmutableMap.copyOf(alexaAppIds);
    }
}
