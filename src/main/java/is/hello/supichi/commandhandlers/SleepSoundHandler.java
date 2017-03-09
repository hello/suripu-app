package is.hello.supichi.commandhandlers;

import com.google.api.client.util.Lists;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.db.sleep_sounds.SleepSoundSettingsDynamoDB;
import com.hello.suripu.core.messeji.Sender;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.SleepSoundSetting;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import is.hello.supichi.commandhandlers.results.GenericResult;
import is.hello.supichi.db.SpeechCommandDAO;
import is.hello.supichi.models.AnnotatedTranscript;
import is.hello.supichi.models.HandlerResult;
import is.hello.supichi.models.HandlerType;
import is.hello.supichi.models.SpeechCommand;
import is.hello.supichi.models.VoiceRequest;
import is.hello.supichi.models.annotations.SleepSoundAnnotation;
import is.hello.supichi.response.SupichiResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static is.hello.supichi.commandhandlers.ErrorText.COMMAND_NOT_FOUND;


/**
 * Created by ksg on 6/17/16
 */
public class SleepSoundHandler extends BaseHandler {
    public enum SoundName {
        NONE("none"),
        AURA("Aura"),
        NOCTURNE("Nocturne"),
        MORPHEUS("Morpheus"),
        HORIZON("Horizon"),
        COSMOS("Cosmos"),
        AUTUMN_WIND("Autumn Wind"),
        FIRESIDE("Fireside"),
        RAINFALL("Rainfall"),
        FOREST_CREEK("Forest Creek"),
        BROWN_NOISE("Brown Noise"),
        WHITE_NOISE("White Noise");

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

        public static String regexPattern() {
            final List<String> names = Lists.newArrayList();
            for (final SoundName soundName : SoundName.values()) {
                names.add(soundName.value.toLowerCase());
            }
            final String regex = String.join("|", names);
            return String.format("play.+(%s)", regex);
        }

    }

    // private static final String PLAY_SLEEP_SOUND_PATTERN = "(play|begin|start)(\\splaying)?\\s?(a|some)?\\s?(sleep|ambient)?\\s(sound)";
    private static final String PLAY_SLEEP_SOUND_PATTERN = "(play|begin|start)(\\splaying)?\\s?(a|some)?\\s?(sleep(ing)?|ambient|night|soothing)?\\s(sound)";

    // TODO: need to get these info from somewhere
    private static final Duration DEFAULT_SLEEP_SOUND_DURATION = Duration.create(2L, "30 Minutes", 1800);
    private static final String DEFAULT_SOUND_NAME = "Rainfall";
    private static final Double SENSE_MAX_DECIBELS = 60.0;
    private static final int DEFAULT_SLEEP_SOUND_VOLUME_PERCENT = 50;

    // Fade in/out sounds over this many seconds on Sense
    private static final Integer FADE_IN = 1;
    private static final Integer FADE_OUT = 1; // Used when explicitly stopped with a Stop message or wave
    private static final Integer TIMEOUT_FADE_OUT = 20; // Used when sense's play duration times out


    private static final Logger LOGGER = LoggerFactory.getLogger(SleepSoundHandler.class);

    private final MessejiClient messejiClient;
    private final SleepSoundsProcessor sleepSoundsProcessor;
    private final SleepSoundSettingsDynamoDB sleepSoundSettingsDynamoDB;
    private Map<String, Sound> availableSounds = Maps.newConcurrentMap();

    final ScheduledThreadPoolExecutor executor;


    public SleepSoundHandler(final MessejiClient messejiClient, final SpeechCommandDAO speechCommandDAO, final SleepSoundsProcessor sleepSoundsProcessor, final SleepSoundSettingsDynamoDB sleepSoundSettingsDynamoDB, final int numThreads) {
        super("sleep_sound", speechCommandDAO, getAvailableActions());
        this.messejiClient = messejiClient;
        this.sleepSoundsProcessor = sleepSoundsProcessor;
        this.sleepSoundSettingsDynamoDB = sleepSoundSettingsDynamoDB;
        executor = new ScheduledThreadPoolExecutor(numThreads);
    }


    private static Map<String, SpeechCommand> getAvailableActions() {
        final Map<String, SpeechCommand> tempMap = Maps.newHashMap();
        tempMap.put(SoundName.regexPattern(), SpeechCommand.SLEEP_SOUND_PLAY);

        tempMap.put("okay play", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play sleep", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play raindrops", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play rain", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play aurora", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play fireplace", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put("play campfire", SpeechCommand.SLEEP_SOUND_PLAY);
        tempMap.put(PLAY_SLEEP_SOUND_PATTERN, SpeechCommand.SLEEP_SOUND_PLAY);

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

        final Optional<SpeechCommand> optionalCommand = getCommand(annotatedTranscript);
        GenericResult result = GenericResult.fail(COMMAND_NOT_FOUND);
        String command = HandlerResult.EMPTY_COMMAND;

        if (optionalCommand.isPresent()) {
            command = optionalCommand.get().getValue();
            if (optionalCommand.get().equals(SpeechCommand.SLEEP_SOUND_PLAY)) {
                result = playSleepSound(request.senseId, request.accountId, annotatedTranscript);
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


    private GenericResult playSleepSound(final String senseId, final Long accountId, final AnnotatedTranscript annotatedTranscript) {

        // TODO: get most recently played sleep_sound_id, order, volume, etc...

        final Duration duration;
        final Integer volumeScalingFactor;

        final Optional<SleepSoundSetting> soundSettingOptional = sleepSoundSettingsDynamoDB.get(senseId, accountId);
        if (soundSettingOptional.isPresent()) {
            duration = soundSettingOptional.get().duration;
            volumeScalingFactor = soundSettingOptional.get().volumeScalingFactor;
        } else {
            duration = DEFAULT_SLEEP_SOUND_DURATION;
            volumeScalingFactor = convertToSenseVolumePercent(SENSE_MAX_DECIBELS, DEFAULT_SLEEP_SOUND_VOLUME_PERCENT);
        }

        final String soundName;
        if (annotatedTranscript.sleepSounds.isEmpty()) {
            soundName = (soundSettingOptional.isPresent()) ? soundSettingOptional.get().sound.name : DEFAULT_SOUND_NAME;
        } else {
            final SleepSoundAnnotation sleepSound = annotatedTranscript.sleepSounds.get(0);
            soundName = sleepSound.sound().value;
        }

        if (!availableSounds.containsKey(soundName)) {
            Optional<Sound> optionalSound = sleepSoundsProcessor.getSoundByFileName(soundName);
            if (optionalSound.isPresent()) {
                availableSounds.put(soundName, optionalSound.get());
            }
        }

        if (!availableSounds.containsKey(soundName)) {
            LOGGER.error("error=invalid-sleep-sound sense_id={} sound_name={}", senseId, soundName);
            return GenericResult.fail("invalid sound name");
        }

        executor.schedule((Runnable) () -> {
            final Optional<Long> messageId = messejiClient.playAudio(
                    senseId,
                    Sender.fromAccountId(accountId),
                    System.nanoTime(),
                    duration,
                    availableSounds.get(soundName),
                    FADE_IN, FADE_OUT,
                    volumeScalingFactor,
                    TIMEOUT_FADE_OUT);
            LOGGER.info("action=messeji-play sense_id={} account_id={}", senseId, accountId);

            if (!messageId.isPresent()) {
                LOGGER.error("error=messeji-request-play-audio-fail sense_id={} account_id={}", senseId, accountId);
            }

        }, 2, TimeUnit.SECONDS);

        // returns true regardless of whether message was properly delivered
        return GenericResult.ok("");
    }

    private GenericResult stopSleepSound(final String senseId, final Long accountId) {
        final Optional<Long> messageId = messejiClient.stopAudio(
                senseId,
                Sender.fromAccountId(accountId),
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
        return SupichiResponseType.SILENT;
    }
}
