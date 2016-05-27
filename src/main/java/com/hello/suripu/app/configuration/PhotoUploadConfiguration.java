package com.hello.suripu.app.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PhotoUploadConfiguration {

    @JsonProperty("bucket_name")
    private String bucketName = "hello-accounts";
    public String bucketName() {
        return bucketName;
    }

    @JsonProperty("profile_prefix")
    private String profilePrefix = "photos/profile/";
    public String profilePrefix() {
        return profilePrefix;
    }

    @JsonProperty("max_upload_size_bytes")
    private Long maxUploadSizeInBytes = 1024L * 1000L * 5L;
    public Long maxUploadSizeInBytes(){
        return maxUploadSizeInBytes;
    }
}
