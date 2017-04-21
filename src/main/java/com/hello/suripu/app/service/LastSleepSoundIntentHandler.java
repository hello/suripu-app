package com.hello.suripu.app.service;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.google.common.base.Optional;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.messeji.Sender;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by jnorgan on 6/16/16.
 */
public class LastSleepSoundIntentHandler extends IntentHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(LastSleepSoundIntentHandler.class);
  private static final String INTENT_NAME = "LastSleepSound";
  private static final Integer FADE_IN = 1;
  private static final Integer FADE_OUT = 1; // Used when explicitly stopped with a Stop message or wave
  private static final Integer TIMEOUT_FADE_OUT = 20; // Used when sense's play duration times out
  private static final Double SENSE_MAX_DECIBELS = 60.0;
  private static final String SOUND_NAME_KEY = "SOUND_NAME";
  private static final HardwareVersion DEFAULT_INTENT_HW_VERSION = HardwareVersion.SENSE_ONE_FIVE;


  final DeviceReadDAO deviceReadDAO;
  final MessejiClient messejiClient;
  final SleepSoundsProcessor sleepSoundsProcessor;
  final DurationDAO durationDAO;

  public LastSleepSoundIntentHandler(final DeviceReadDAO deviceReadDAO,
                                     final SleepSoundsProcessor sleepSoundsProcessor,
                                     final DurationDAO durationDAO,
                                     final MessejiClient messejiClient) {
    this.deviceReadDAO = deviceReadDAO;
    this.messejiClient = messejiClient;
    this.sleepSoundsProcessor = sleepSoundsProcessor;
    this.durationDAO = durationDAO;
  }

  @Override
  public SpeechletResponse handleIntentInternal(final Intent intent, final Session session, final AccessToken accessToken) {

    LOGGER.debug("action=alexa-intent-lastsound account_id={}", accessToken.accountId.toString());

//    return IntentHandler.buildLinkAccountResponse();
    final Optional<DeviceAccountPair> optionalPair = deviceReadDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
    if(!optionalPair.isPresent()) {
      return errorResponse("No Account Found!");
    }
    final DeviceAccountPair accountPair = optionalPair.get();

    final Optional<Duration> durationOptional = durationDAO.getById(11L);
    if (!durationOptional.isPresent()) {
      LOGGER.warn("dao=durationDAO method=getById id={} error=not-found", 11L);
      return errorResponse("invalid duration");
    }

    LOGGER.debug("Duration: {}", durationOptional.get().name);

    String speechText;
    String slotName;
    if (session.getAttributes().containsKey(SOUND_NAME_KEY)) {
      final String lastSound = (String) session.getAttribute(SOUND_NAME_KEY);
      speechText = String.format("The last sound you asked me to play was %s. Playing again.", lastSound);
      slotName = lastSound;
    } else {
      LOGGER.debug("{} key is null", SOUND_NAME_KEY);
      speechText = "I don't remember what you last played. Playing the Horizon sleep sound.";
      slotName = "Horizon";
    }

    final Optional<Sound> soundOptional = sleepSoundsProcessor.getSoundByFileName(WordUtils.capitalize(slotName), DEFAULT_INTENT_HW_VERSION);
    if (!soundOptional.isPresent()) {
      return errorResponse("Invalid Sound ID");
    }

    LOGGER.debug("Sound Name: {}", soundOptional.get().name);
    LOGGER.debug("Sound URL: {}", soundOptional.get().url);

    final Integer volumeScalingFactor = convertToSenseVolumePercent(100);

    final Optional<Long> messageId = messejiClient.playAudio(
        accountPair.externalDeviceId, Sender.fromAccountId(accountPair.accountId), System.currentTimeMillis(),
        durationOptional.get(), soundOptional.get(), 0, FADE_OUT, volumeScalingFactor, TIMEOUT_FADE_OUT);

    if (messageId.isPresent()) {
      LOGGER.debug("messeji-status=success message-id={} sense-id={}", messageId.get(), accountPair.externalDeviceId);
      return buildSpeechletResponse(speechText, false);
    } else {
      LOGGER.error("messeji-status=failure sense-id={}", accountPair.externalDeviceId);
      return buildSpeechletResponse("There was a problem attempting to play your sleep sound.", true);
    }
  }

  @Override
  public String getIntentName() {
    return INTENT_NAME;
  }

  protected static Integer convertToSenseVolumePercent(final Double maxDecibels,
                                                       final Integer volumePercent) {
    if (volumePercent > 100 || volumePercent < 0) {
      throw new IllegalArgumentException(String.format("volumePercent must be in the range [0, 100], not %s", volumePercent));
    } else if (volumePercent <= 1) {
      return 0;
    }
    // Formula/constants obtained from http://www.sengpielaudio.com/calculator-loudness.htm
    final double decibelOffsetFromMaximum = 33.22 * Math.log10(volumePercent / 100.0);
    final double decibels = maxDecibels + decibelOffsetFromMaximum;
    return (int) Math.round((decibels / maxDecibels) * 100);
  }

  protected static Integer convertToSenseVolumePercent(final Integer volumePercent) {
    return convertToSenseVolumePercent(SENSE_MAX_DECIBELS, volumePercent);
  }
}
