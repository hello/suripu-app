package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class Share {

    abstract Long accountId();
    abstract String type();
    abstract String payload();

    abstract ObjectMapper mapper();
}
