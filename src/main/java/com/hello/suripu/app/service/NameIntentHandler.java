package com.hello.suripu.app.service;

import com.google.common.base.Optional;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.coredropwizard.oauth.AccessToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by jnorgan on 6/16/16.
 */
public class NameIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(NameIntentHandler.class);
  private static final String INTENT_NAME = "GetName";

  private final AccountDAO accountDAO;

  public NameIntentHandler(final AccountDAO accountDAO) {
    this.accountDAO = accountDAO;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    //Get Username
    final Optional<Account> optionalAccount = accountDAO.getById(accessToken.accountId);
    if(!optionalAccount.isPresent()) {
      return errorResponse("I can't seem to find your name.");
    }
    final Account account = optionalAccount.get();
    return buildSpeechletResponse(String.format("Your name is %s.", account.name()), true);
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }
}
