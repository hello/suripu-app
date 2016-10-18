package is.hello.speech.commandhandlers;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import is.hello.speech.db.SpeechCommandDAO;
import is.hello.speech.commandhandlers.results.GenericResult;
import is.hello.speech.models.AnnotatedTranscript;
import is.hello.speech.models.HandlerResult;
import is.hello.speech.models.HandlerType;
import is.hello.speech.models.SpeechCommand;
import is.hello.speech.models.VoiceRequest;
import is.hello.speech.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static is.hello.speech.commandhandlers.ErrorText.COMMAND_NOT_FOUND;


/**
 * Created by ksg on 6/17/16
 */
public class SleepSoundHandler extends BaseHandler {
    public enum SoundName {
        NONE("none"),
        AURA("aura"),
        NOCTURNE("nocturne"),
        MORPHEUS("morpheus"),
        HORIZON("horizon"),
        COSMOS("cosmos"),
        AUTUMN_WIND("autumn wind"),
        FIRESIDE("fireside"),
        RAINFALL("rainfall"),
        FOREST_CREEK("forest creek"),
        BROWN_NOISE("brown noise"),
        WHITE_NOISE("white noise");

        public final String value;

        SoundName(String value) {
            this.value = value;
        }

        public static SoundName fromString(final String text) {
            if (text != null) {
                for (final SoundName soundName : SoundName.values()) {
                    if (text.equalsIgnoreCase(soundName.toString()))
                        return soundName;
                }
            }
            return SoundName.NONE;
        }

    }

    // TODO: need to get these info from somewhere
    private static final Duration DEFAULT_SLEEP_SOUND_DURATION = Duration.create(2L, "30 Minutes", 1800);
    private static final Sound DEFAULT_SOUND = Sound.create(20L,
            "https://s3.amazonaws.com/hello-audio/sleep-tones-preview/Rainfall.mp3",
            "Rainfall",
            "/SLPTONES/ST006.RAW",
            "s3://hello-audio/sleep-tones-raw/2016-04-01/ST006.raw"
    );
    private static final long DEFAULT_SLEEP_SOUND_ID =  20L; //
    private static final Double SENSE_MAX_DECIBELS = 60.0;
    private static final int DEFAULT_SLEEP_SOUND_VOLUME_PERCENT = 70;

    // Fade in/out sounds over this many seconds on Sense
    private static final Integer FADE_IN = 1;
    private static final Integer FADE_OUT = 1; // Used when explicitly stopped with a Stop message or wave
    private static final Integer TIMEOUT_FADE_OUT = 20; // Used when sense's play duration times out


    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSoundHandler.class);

    private final MessejiClient messejiClient;
    private final SleepSoundsProcessor sleepSoundsProcessor;

    final ScheduledThreadPoolExecutor executor;


    public SleepSoundHandler(final MessejiClient messejiClient, final SpeechCommandDAO speechCommandDAO, final SleepSoundsProcessor sleepSoundsProcessor, final int numThreads) {
        super("sleep_sound", speechCommandDAO, getAvailableActions());
        this.messejiClient = messejiClient;
        this.sleepSoundsProcessor = sleepSoundsProcessor;
        executor = new ScheduledThreadPoolExecutor(numThreads);
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        // TODO read from DynamoDB
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put("okay play", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sound", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sleep sound", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sleep", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("white noise", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("White Noise", SpeechCommand.SLEEP_SOUND_PLAY);

        tempMap.put("stop", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sound", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sounds", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sleep", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sleep sounds", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stop sleep sound", SpeechCommand.SLEEP_SOUND_STOP);
        tempMap.put("stopping sound", SpeechCommand.SLEEP_SOUND_STOP);
        return tempMap;
    }

    @Override
    public HandlerResult executeCommand(final AnnotatedTranscript annotatedTranscript, final VoiceRequest request) {
        final String text = annotatedTranscript.transcript;

        final Optional<SpeechCommand> optionalCommand = getCommand(text);
        GenericResult result = GenericResult.fail(COMMAND_NOT_FOUND);
        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            if (optionalCommand.get().equals(SpeechCommand.SLEEP_SOUND_PLAY)) {
                result = playSleepSound(request.senseId, request.accountId);
            } else if (optionalCommand.get().equals(SpeechCommand.SLEEP_SOUND_STOP)) {
                result = stopSleepSound(request.senseId, request.accountId);
            }
        }

        return new HandlerResult(HandlerType.SLEEP_SOUNDS, command, result);

    }

    @Override
    public Integer matchAnnotations(final AnnotatedTranscript annotatedTranscript) {
        // sound-name + duration
        return annotatedTranscript.sleepSounds.size() + annotatedTranscript.durations.size();
    }


    private GenericResult playSleepSound(final String senseId, final Long accountId) {

        // TODO: get most recently played sleep_sound_id, order, volume, etc...
        // final Optional<Sound> soundOptional = sleepSoundsProcessor.getSound(senseId, DEFAULT_SLEEP_SOUND_ID);
        final Optional<Sound> soundOptional = Optional.of(DEFAULT_SOUND);
        if (!soundOptional.isPresent()) {
            LOGGER.error("error=invalid-sound-id id={} sense_id={}", DEFAULT_SLEEP_SOUND_ID, senseId);
            return GenericResult.fail("invalid sound id");
        }

        final Integer volumeScalingFactor = convertToSenseVolumePercent(SENSE_MAX_DECIBELS, DEFAULT_SLEEP_SOUND_VOLUME_PERCENT);


        executor.schedule((Runnable) () -> {
            final Optional<Long> messageId = messejiClient.playAudio(
                    senseId,
                    MessejiClient.Sender.fromAccountId(accountId),
                    System.nanoTime(),
                    DEFAULT_SLEEP_SOUND_DURATION,
                    soundOptional.get(),
                    FADE_IN, FADE_OUT,
                    volumeScalingFactor,
                    TIMEOUT_FADE_OUT);
            LOGGER.info("action=messeji-play sense_id={} account_id={}", senseId, accountId);

            if (!messageId.isPresent()) {
                LOGGER.error("error=messeji-request-play-audio-fail sense_id={} account_id={}", senseId, accountId);
            }

        }, 2, TimeUnit.SECONDS);

        // returns true regardless of whether message was properly delivered
        return GenericResult.ok("Goodnight");
    }

    private GenericResult stopSleepSound(final String senseId, final Long accountId) {
        final Optional<Long> messageId = messejiClient.stopAudio(
                senseId,
                MessejiClient.Sender.fromAccountId(accountId),
                System.nanoTime(),
                FADE_OUT);

        if (messageId.isPresent()) {
            return GenericResult.ok("");
        } else {
            LOGGER.error("error=messeji-request-stop-audio-fail sense_id={}, account_id={}", senseId, accountId);
            return GenericResult.fail("stop sound fail");
        }
    }

    private static Integer convertToSenseVolumePercent(final Double maxDecibels,
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

    @Override
    public SupichiResponseType responseType() {
        return SupichiResponseType.WATSON;
    }
}
