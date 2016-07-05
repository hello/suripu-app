package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ShareRequest {

    public final String id;
    public final String type;

    private ShareRequest(final String id, final String type) {
        this.id = id;
        this.type = type;
    }

    @JsonCreator
    public static ShareRequest create(@JsonProperty("id") final String id, @JsonProperty("type") final String type) {
        return new ShareRequest(id, type);
    }
}
