package com.hello.suripu.app.service;

import com.google.common.collect.ImmutableList;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;

/**
 * Created by jnorgan on 7/12/16.
 */
public interface TestVoiceResponsesDAO {

  @SqlQuery("SELECT response_template FROM voice_test_responses WHERE intent_name = CAST(:intent_name AS INTENT_NAME) ORDER BY id ASC")
  public ImmutableList<String> getAllResponsesByIntent(@Bind("intent_name") String intentName);
}

