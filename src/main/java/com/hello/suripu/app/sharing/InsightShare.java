package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.models.Insights.InsightCard;

public class InsightShare extends Share {

    private final InsightCard insight;
    private final Long accountId;
    private final ObjectMapper mapper;

    private InsightShare(final InsightCard insight, final Long accountId, final ObjectMapper mapper) {
        this.insight = insight;
        this.accountId = accountId;
        this.mapper = mapper;
    }

    public static InsightShare create(final InsightCard insight, final Long accountId, final ObjectMapper mapper) {
        return new InsightShare(insight, accountId, mapper);
    }

    @Override
    public Long accountId() {
        return accountId;
    }

    @Override
    public String type() {
        return "insight";
    }

    @Override
    public String payload() {
        try {
            return mapper.writeValueAsString(insight);
        } catch (JsonProcessingException e) {
        }
        return "";
    }

    @Override
    ObjectMapper mapper() {
        return mapper;
    }
}
