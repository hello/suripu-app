package com.hello.suripu.app.sharing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.hello.suripu.core.models.Insights.InfoInsightCards;
import com.hello.suripu.core.models.Insights.InsightCard;

public class InsightShare extends Share {

    private final InsightCard insight;
    private final Long accountId;
    private final ObjectMapper mapper;
    private final String name;
    private final Optional<InfoInsightCards> info;

    private InsightShare(final InsightCard insight, final Long accountId, final String name, final Optional<InfoInsightCards> infoInsightCards, final ObjectMapper mapper) {
        this.insight = insight;
        this.accountId = accountId;
        this.name = name;
        this.info = infoInsightCards;
        this.mapper = mapper;
    }

    public static InsightShare create(final InsightCard insight, final Long accountId, final String name, final Optional<InfoInsightCards> info, final ObjectMapper mapper) {
        return new InsightShare(insight, accountId, name, info, mapper);
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
    String name() {
        return name;
    }

    @Override
    String info() {
        try {
            return mapper.writeValueAsString(info);
        } catch (JsonProcessingException e) {
        }
        return "";
    }

    @Override
    ObjectMapper mapper() {
        return mapper;
    }
}
