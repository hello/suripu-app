package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.core.configuration.UrlName;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.coredropwizard.configuration.FirehoseConfiguration;
import com.hello.suripu.coredropwizard.configuration.GraphiteConfiguration;
import com.hello.suripu.coredropwizard.configuration.KinesisConfiguration;
import com.hello.suripu.coredropwizard.configuration.MessejiHttpClientConfiguration;
import com.hello.suripu.coredropwizard.configuration.NewDynamoDBConfiguration;
import com.hello.suripu.coredropwizard.configuration.PushNotificationsConfiguration;
import com.hello.suripu.coredropwizard.configuration.S3BucketConfiguration;
import com.hello.suripu.coredropwizard.configuration.TaimurainConfiguration;
import com.hello.suripu.coredropwizard.configuration.TimelineAlgorithmConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.db.DataSourceFactory;
import is.hello.supichi.configuration.SpeechConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class SuripuAppConfiguration extends Configuration {

    @Valid
    @NotNull
    @JsonProperty("agg_stats_version")
    private String aggStatsVersion;
    public String getAggStatsVersion() {
        return this.aggStatsVersion;
    }

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
    @JsonProperty("taimurain_configuration")
    private TaimurainConfiguration taimurainConfiguration;
    public TaimurainConfiguration getTaimurainConfiguration() {
        return taimurainConfiguration;
    }

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
    @JsonProperty("urls")
    private Map<UrlName, String> urlMap = Maps.newHashMap();
    public Map<UrlName, String> getUrlMap() { return this.urlMap; }
    public String getUrl(@NotNull final UrlName key) { return this.urlMap.get(key);}

    @Valid
    @JsonProperty("alexa_app_ids")
    private Map<String, String> alexaAppIds = Maps.newHashMap();
    public ImmutableMap<String, String> getAlexaAppIds() {
        return ImmutableMap.copyOf(alexaAppIds);
    }

    @JsonProperty("segment_write_key")
    private String segmentWriteKey = "hello";
    public String segmentWriteKey() {
        return segmentWriteKey;
    }

    @JsonProperty("keys_management_service")
    private KMSConfiguration kmsConfiguration;
    public KMSConfiguration kmsConfiguration() { return this.kmsConfiguration; }

    @JsonProperty("expansions")
    private ExpansionConfiguration expansionConfiguration;
    public ExpansionConfiguration expansionConfiguration() { return this.expansionConfiguration; }

    @JsonProperty("speech")
    private SpeechConfiguration speechConfiguration;
    public SpeechConfiguration speechConfiguration() { return this.speechConfiguration; }

    @JsonProperty("s3_endpoint")
    private String s3Endpoint;
    public String s3Endpoint() { return s3Endpoint; }

    @JsonProperty("action_firehose")
    private FirehoseConfiguration firehoseConfiguration;
    public FirehoseConfiguration firehoseConfiguration() { return firehoseConfiguration; }


    @JsonProperty("available_sensors")
    private Map<HardwareVersion, List<Sensor>> availableSensors = Maps.newHashMap();
    public Map<HardwareVersion, List<Sensor>> availableSensors() {
        return availableSensors;
    }

    @JsonProperty("export_data_queue_url")
    private String exportDataQueueUrl = "";
    public String exportDataQueueUrl() {
        return exportDataQueueUrl;
    }
}

