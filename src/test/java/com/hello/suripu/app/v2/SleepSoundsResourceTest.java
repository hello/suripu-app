package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.hello.suripu.api.input.FileSync;
import com.hello.suripu.api.input.State;
import com.hello.suripu.app.modules.RolloutAppModule;
import com.hello.suripu.core.ObjectGraphRoot;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.FeatureStore;
import com.hello.suripu.core.db.FileInfoDAO;
import com.hello.suripu.core.db.FileManifestDAO;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.SenseStateDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.flipper.FeatureFlipper;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceKeyStoreRecord;
import com.hello.suripu.core.models.Feature;
import com.hello.suripu.core.models.FileInfo;
import com.hello.suripu.core.models.SenseStateAtTime;
import com.hello.suripu.core.models.sleep_sounds.Duration;
import com.hello.suripu.core.models.sleep_sounds.SleepSoundStatus;
import com.hello.suripu.core.models.sleep_sounds.Sound;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.coredropwizard.clients.MessejiClient;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Created by jakepiccolo on 2/22/16.
 */
public class SleepSoundsResourceTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(SleepSoundsResourceTest.class);
    private final Long accountId = 1L;
    private final String senseId = "sense";

    private final Optional<DeviceAccountPair> pair = Optional.of(new DeviceAccountPair(accountId, 1L, senseId, new DateTime()));
    private final DeviceKeyStoreRecord record = DeviceKeyStoreRecord.forSense("sense","key","sn", "", HardwareVersion.SENSE_ONE);
    private final AccessToken token = makeToken(accountId);

    private DeviceDAO deviceDAO;
    private SenseStateDynamoDB senseStateDynamoDB;
    private KeyStore keyStore;
    private DurationDAO durationDAO;
    private MessejiClient messejiClient;
    private FileInfoDAO fileInfoDAO;
    private FileManifestDAO fileManifestDAO;
    private SleepSoundsResource sleepSoundsResource;
    private FeatureStore featureStore;
    private ActionProcessor actionProcessor;
    private SleepSoundsProcessor sleepSoundsProcessor;
    private Optional<DeviceKeyStoreRecord> of;

    @Before
    public void setUp() {
        featureStore = Mockito.mock(FeatureStore.class);
        actionProcessor = Mockito.mock(ActionProcessor.class);
        when(featureStore.getData())
                .thenReturn(
                        ImmutableMap.of(
                                FeatureFlipper.SLEEP_SOUNDS_ENABLED,
                                new Feature("sleep_sounds_enabled", Collections.EMPTY_LIST, Collections.EMPTY_LIST, (float) 100.0)));
        final RolloutAppModule module = new RolloutAppModule(featureStore, 30, actionProcessor);
        ObjectGraphRoot.getInstance().init(module);

        deviceDAO = Mockito.mock(DeviceDAO.class);
        senseStateDynamoDB = Mockito.mock(SenseStateDynamoDB.class);
        keyStore = Mockito.mock(KeyStore.class);
        durationDAO = Mockito.mock(DurationDAO.class);
        messejiClient = Mockito.mock(MessejiClient.class);
        fileInfoDAO = Mockito.mock(FileInfoDAO.class);
        fileManifestDAO = Mockito.mock(FileManifestDAO.class);
        sleepSoundsProcessor = SleepSoundsProcessor.create(fileInfoDAO, fileManifestDAO);
        sleepSoundsResource = SleepSoundsResource.create(
                durationDAO, senseStateDynamoDB, keyStore, deviceDAO,
                messejiClient, sleepSoundsProcessor, 1, 1);
    }

    private void assertEmpty(final SleepSoundStatus status) {
        assertThat(status.isPlaying, is(false));
        assertThat(status.duration.isPresent(), is(false));
        assertThat(status.sound.isPresent(), is(false));
    }

    private static AccessToken makeToken(final Long accountId) {
        return new AccessToken.Builder()
                .withAccountId(accountId)
                .withCreatedAt(DateTime.now())
                .withExpiresIn(DateTime.now().plusHours(1).getMillis())
                .withRefreshExpiresIn(DateTime.now().plusHours(1).getMillis())
                .withRefreshToken(UUID.randomUUID())
                .withToken(UUID.randomUUID())
                .withScopes(new OAuthScope[]{ OAuthScope.USER_BASIC })
                .withAppId(1L)
                .build();
    }

    // region getStatus
    @Test
    public void testGetStatusNoDevicePaired() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(Optional.<DeviceAccountPair>absent());
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusNoState() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        when(senseStateDynamoDB.getState(senseId)).thenReturn(Optional.<SenseStateAtTime>absent());
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusNoAudioState() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final SenseStateAtTime state = new SenseStateAtTime(State.SenseState.newBuilder().setSenseId(senseId).build(), new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusAudioNotPlaying() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder().setPlayingAudio(false).build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusInconsistentState() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder().setPlayingAudio(true).build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusNoDuration() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder()
                                .setPlayingAudio(true)
                                .setFilePath("path")
                                .build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusNoFilePath() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder()
                                .setPlayingAudio(true)
                                .setDurationSeconds(1)
                                .build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusInvalidDuration() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        when(durationDAO.getDurationBySeconds(Mockito.anyInt())).thenReturn(Optional.<Duration>absent());
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder()
                                .setPlayingAudio(true)
                                .setFilePath("path")
                                .setDurationSeconds(1)
                                .build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    @Test
    public void testGetStatusInvalidPath() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        when(durationDAO.getDurationBySeconds(Mockito.anyInt())).thenReturn(Optional.of(Duration.create(1L, "path", 30)));
        when(fileInfoDAO.getByFilePath(Mockito.anyString())).thenReturn(Optional.<FileInfo>absent());
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder()
                                .setPlayingAudio(true)
                                .setFilePath("path")
                                .setDurationSeconds(1)
                                .build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);
        assertEmpty(status);
    }

    private FileInfo makeFileInfo(final Long id, final String preview, final String name, final String path, final String url) {
        return FileInfo.newBuilder()
                .withFileType(FileInfo.FileType.SLEEP_SOUND)
                .withId(id)
                .withPreviewUri(preview)
                .withName(name)
                .withPath(path)
                .withUri(url)
                .withSha(path)
                .withIsPublic(true)
                .withFirmwareVersion(1)
                .build();
    }

    @Test
    public void testGetStatusAllCorrect() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(Mockito.anyLong())).thenReturn(pair);
        final Duration duration = Duration.create(1L, "path", 30);
        when(durationDAO.getDurationBySeconds(Mockito.anyInt())).thenReturn(Optional.of(duration));
        final FileInfo fileInfo = makeFileInfo(1L, "preview", "name", "path", "url");
        when(fileInfoDAO.getByFilePath(Mockito.anyString())).thenReturn(Optional.of(fileInfo));
        final SenseStateAtTime state = new SenseStateAtTime(
                State.SenseState.newBuilder()
                        .setSenseId(senseId)
                        .setAudioState(State.AudioState.newBuilder()
                                .setPlayingAudio(true)
                                .setFilePath("path")
                                .setDurationSeconds(1)
                                .build())
                        .build(),
                new DateTime());
        when(senseStateDynamoDB.getState(senseId))
                .thenReturn(Optional.of(state));
        final SleepSoundStatus status = sleepSoundsResource.getStatus(token);

        assertThat(status.isPlaying, is(true));
        assertThat(status.sound.isPresent(), is(true));
        assertThat(status.duration.isPresent(), is(true));
        assertThat(status.duration.get(), is(duration));
        assertThat(status.sound.get(), is(Sound.fromFileInfo(fileInfo)));
        assertThat(status.volumePercent.isPresent(), is(false));
    }
    // endregion getStatus

    // region play
    @Test
    public void testPlayRequestValidation() throws Exception {
        final Long durationId = 1L;
        final Long soundId = 1L;
        final Long order = 1L;
        final Integer volumePercent = 50;

        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);

        final FileInfo fileInfo = FileInfo.newBuilder()
                .withId(soundId)
                .withPreviewUri("preview")
                .withName("name")
                .withPath("/path/to/file")
                .withUri("url")
                .withFirmwareVersion(1)
                .withIsPublic(true)
                .withSha("11")
                .withFileType(FileInfo.FileType.SLEEP_SOUND)
                .build();
        final FileSync.FileManifest fileManifest = FileSync.FileManifest.newBuilder()
                .addFileInfo(FileSync.FileManifest.File.newBuilder()
                        .setDownloadInfo(FileSync.FileManifest.FileDownload.newBuilder()
                                .setSdCardFilename("file")
                                .setSdCardPath("path/to")
                                .build())
                        .build())
                .build();
        final Duration duration = Duration.create(durationId, "name", 30);

        // Only work for our specific sound
        when(fileInfoDAO.getById(Mockito.anyLong())).thenReturn(Optional.<FileInfo>absent());
        when(fileInfoDAO.getById(soundId)).thenReturn(Optional.of(fileInfo));

        when(fileManifestDAO.getManifest(Mockito.anyString())).thenReturn(Optional.<FileSync.FileManifest>absent());
        when(fileManifestDAO.getManifest(senseId)).thenReturn(Optional.of(fileManifest));

        // Only work for our specific duration
        when(durationDAO.getById(Mockito.anyLong())).thenReturn(Optional.<Duration>absent());
        when(durationDAO.getById(durationId)).thenReturn(Optional.of(duration));
        when(keyStore.getKeyStoreRecord(anyString())).thenReturn(Optional.of(record));
        // TEST invalid sound
        assertThat(
                sleepSoundsResource.play(
                        token, SleepSoundsResource.PlayRequest.create(soundId + 1, durationId, order, volumePercent)
                ).getStatus(),
                is(Response.Status.BAD_REQUEST.getStatusCode()));

        // TEST invalid duration
        assertThat(
                sleepSoundsResource.play(
                        token, SleepSoundsResource.PlayRequest.create(soundId, durationId + 1, order, volumePercent)
                ).getStatus(),
                is(Response.Status.BAD_REQUEST.getStatusCode()));

        // TEST no sense for account
        final Long badAccountId = accountId + 100;
        final AccessToken badToken = makeToken(badAccountId);
        when(deviceDAO.getMostRecentSensePairByAccountId(badAccountId)).thenReturn(Optional.<DeviceAccountPair>absent());
        assertThat(
                sleepSoundsResource.play(
                        badToken, SleepSoundsResource.PlayRequest.create(soundId, durationId, order, volumePercent)
                ).getStatus(),
                is(Response.Status.BAD_REQUEST.getStatusCode()));
    }
    // endregion play

    // region getSounds
    @Test
    public void testGetSoundsNoManifest() throws Exception {
        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);

        when(fileManifestDAO.getManifest(Mockito.anyString())).thenReturn(Optional.<FileSync.FileManifest>absent());
        when(keyStore.getKeyStoreRecord(anyString())).thenReturn(Optional.of(record));
        final SleepSoundsProcessor.SoundResult soundResult = sleepSoundsResource.getSounds(token);
        assertThat(soundResult.sounds.size(), is(0));
        assertThat(soundResult.state, is(SleepSoundsProcessor.SoundResult.State.SENSE_UPDATE_REQUIRED));
    }

    @Test
    public void testGetSounds() throws Exception {
        final Long soundId = 1L;
        final int numSounds = 11;


        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);

        final List<FileInfo> fileInfoList = Lists.newArrayList();
        for (int i = 0; i < numSounds; i++) {
            fileInfoList.add(FileInfo.newBuilder()
                    .withId(soundId + i)
                    .withPreviewUri("preview")
                    .withName("name")
                    .withPath("/path/to/file")
                    .withUri("url")
                    .withFirmwareVersion(1)
                    .withIsPublic(true)
                    .withSha("11")
                    .withFileType(FileInfo.FileType.SLEEP_SOUND)
                    .build());
        }
        fileInfoList.add(FileInfo.newBuilder()
                .withId(soundId + fileInfoList.size())
                .withPreviewUri("preview")
                .withName("name")
            .withPath("/path/to/file")
                .withUri("url")
                .withFirmwareVersion(1)
                .withIsPublic(true)
                .withSha("11")
                .withFileType(FileInfo.FileType.ALARM)
                .build());

        when(fileInfoDAO.getAll(Mockito.anyInt(), Mockito.anyString())).thenReturn(fileInfoList);

        final FileSync.FileManifest fileManifest = FileSync.FileManifest.newBuilder()
                .addFileInfo(FileSync.FileManifest.File.newBuilder()
                        .setDownloadInfo(FileSync.FileManifest.FileDownload.newBuilder()
                                .setSdCardFilename("file")
                                .setSdCardPath("path/to")
                                .setSha1(ByteString.copyFrom(Hex.decodeHex("11".toCharArray())))
                                .build())
                        .build())
                .build();
        when(fileManifestDAO.getManifest(Mockito.anyString())).thenReturn(Optional.of(fileManifest));

        when(keyStore.getKeyStoreRecord(anyString())).thenReturn(Optional.of(record));
        final SleepSoundsProcessor.SoundResult soundResult = sleepSoundsResource.getSounds(token);
        assertThat(soundResult.state, is(SleepSoundsProcessor.SoundResult.State.OK));
        assertThat(soundResult.sounds.size(), is(numSounds));
        for (int i = 0; i < soundResult.sounds.size(); i++) {
            assertThat(soundResult.sounds.get(i).id, is(soundId + i));
        }
    }

    @Test
    public void testGetSoundsNotEnoughSounds() throws Exception {
        final Long soundId = 1L;

        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);

        final List<FileInfo> fileInfoList = Lists.newArrayList();
        for (int i = 0; i < 3; i++) {
            fileInfoList.add(FileInfo.newBuilder()
                    .withId(soundId + i)
                    .withPreviewUri("preview")
                    .withName("name")
                    .withPath("/path/to/file")
                    .withUri("url")
                    .withFirmwareVersion(1)
                    .withIsPublic(true)
                    .withSha("11")
                    .withFileType(FileInfo.FileType.SLEEP_SOUND)
                    .build());
        }
        fileInfoList.add(FileInfo.newBuilder()
            .withId(soundId + fileInfoList.size())
            .withPreviewUri("preview")
            .withName("name")
            .withPath("/wrong/path/to/file")
            .withUri("url")
            .withFirmwareVersion(1)
            .withIsPublic(true)
            .withSha("11")
            .withFileType(FileInfo.FileType.SLEEP_SOUND)
            .build());

        when(fileInfoDAO.getAll(Mockito.anyInt(), Mockito.anyString())).thenReturn(fileInfoList);

        final FileSync.FileManifest fileManifest = FileSync.FileManifest.newBuilder()
                .addFileInfo(FileSync.FileManifest.File.newBuilder()
                        .setDownloadInfo(FileSync.FileManifest.FileDownload.newBuilder()
                                .setSdCardFilename("file")
                                .setSdCardPath("path/to")
                                .setSha1(ByteString.copyFrom(Hex.decodeHex("11".toCharArray())))
                                .build())
                        .build())
                .build();
        when(fileManifestDAO.getManifest(Mockito.anyString())).thenReturn(Optional.of(fileManifest));
        when(keyStore.getKeyStoreRecord(anyString())).thenReturn(Optional.of(record));
        final SleepSoundsProcessor.SoundResult soundResult = sleepSoundsResource.getSounds(token);
        assertThat(soundResult.sounds.size(), is(0));
        assertThat(soundResult.state, is(SleepSoundsProcessor.SoundResult.State.SOUNDS_NOT_DOWNLOADED));
    }

    @Test(expected = WebApplicationException.class)
    public void testGetSoundsNotFeatureFlipped() {
        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);
        when(featureStore.getData())
                .thenReturn(
                        ImmutableMap.of(
                                FeatureFlipper.SLEEP_SOUNDS_ENABLED,
                                new Feature("sleep_sounds_enabled", Collections.EMPTY_LIST, Collections.EMPTY_LIST, (float) 0.0)));
        final RolloutAppModule module = new RolloutAppModule(featureStore, 30, actionProcessor);
        ObjectGraphRoot.getInstance().init(module);
        sleepSoundsResource = SleepSoundsResource.create(
                durationDAO, senseStateDynamoDB, keyStore, deviceDAO,
                messejiClient, sleepSoundsProcessor, 1, 1);
        sleepSoundsResource.getSounds(token);
    }

    // endregion getSounds

    // region convertToSenseVolumePercent
    @Test
    public void testConvertToSenseVolumePercent() throws Exception {
        assertThat(SleepSoundsResource.convertToSenseVolumePercent(100), is(100));
        assertThat(SleepSoundsResource.convertToSenseVolumePercent(50), is(83));
        assertThat(SleepSoundsResource.convertToSenseVolumePercent(25), is(67));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertToSenseVolumePercentTooLarge() {
        SleepSoundsResource.convertToSenseVolumePercent(200);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertToSenseVolumePercentTooSmall() {
        SleepSoundsResource.convertToSenseVolumePercent(-1);
    }
    // endregion convertToSenseVolumePercent

    // region convertToDisplayVolumePercent
    @Test
    public void testConvertToDisplayVolumePercent() throws Exception {
        assertThat(SleepSoundsResource.convertToDisplayVolumePercent(100), is(100));
        assertThat(SleepSoundsResource.convertToDisplayVolumePercent(83), is(50));
        assertThat(SleepSoundsResource.convertToDisplayVolumePercent(67), is(25));

        for (int i = 5; i <= 100; i+=5) {
            assertThat(SleepSoundsResource.convertToDisplayVolumePercent(SleepSoundsResource.convertToSenseVolumePercent(i)), is(i));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertToDisplayVolumePercentTooLarge() {
        SleepSoundsResource.convertToDisplayVolumePercent(200);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertToDisplayVolumePercentTooSmall() {
        SleepSoundsResource.convertToDisplayVolumePercent(-1);
    }
    // endregion convertToDisplayVolumePercent

    // region getCombinedState
    @Test(expected = WebApplicationException.class)
    public void testGetCombinedStateNoDevicePair() {
        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(Optional.<DeviceAccountPair>absent());
        sleepSoundsResource.getCombinedState(token);
    }

    @Test(expected = WebApplicationException.class)
    public void testGetCombinedStateFeatureDisabled() {
        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);
        when(featureStore.getData())
                .thenReturn(
                        ImmutableMap.of(
                                FeatureFlipper.SLEEP_SOUNDS_ENABLED,
                                new Feature("sleep_sounds_enabled", Collections.EMPTY_LIST, Collections.EMPTY_LIST, (float) 0.0)));
        final RolloutAppModule module = new RolloutAppModule(featureStore, 30, actionProcessor);
        ObjectGraphRoot.getInstance().init(module);
        sleepSoundsResource = SleepSoundsResource.create(
                durationDAO, senseStateDynamoDB, keyStore, deviceDAO,
                messejiClient, sleepSoundsProcessor, 1, 1);
        sleepSoundsResource.getCombinedState(token);
    }

    @Test
    public void testGetCombinedState() {
        when(deviceDAO.getMostRecentSensePairByAccountId(accountId)).thenReturn(pair);

        final SleepSoundsProcessor mockedSleepSoundsProcessor = Mockito.mock(SleepSoundsProcessor.class);
        sleepSoundsResource = SleepSoundsResource.create(
                durationDAO, senseStateDynamoDB, keyStore, deviceDAO,
                messejiClient, mockedSleepSoundsProcessor, 1, 1);

        final List<Sound> sounds = ImmutableList.of(Sound.create(1L, "preview", "name", "filePath", "url"));
        when(mockedSleepSoundsProcessor.getSounds(senseId, HardwareVersion.SENSE_ONE)).thenReturn(new SleepSoundsProcessor.SoundResult(sounds, SleepSoundsProcessor.SoundResult.State.OK));

        final List<Duration> durations = ImmutableList.of(Duration.create(2L, "duration", 30));
        when(durationDAO.all()).thenReturn(durations);
        when(keyStore.getKeyStoreRecord(anyString())).thenReturn(Optional.of(record));
        final SenseStateAtTime state = new SenseStateAtTime(State.SenseState.newBuilder()
                .setSenseId(senseId)
                .setAudioState(State.AudioState.newBuilder()
                        .setDurationSeconds(30)
                        .setFilePath("filePath")
                        .setPlayingAudio(true)
                        .setVolumePercent(100)
                        .build())
                .build(), new DateTime());
        when(senseStateDynamoDB.getState(senseId)).thenReturn(Optional.of(state));

        final SleepSoundsResource.CombinedState combinedState = sleepSoundsResource.getCombinedState(token);
        assertThat(combinedState.durationResult.durations, is(durations));
        assertThat(combinedState.soundResult.sounds, is(sounds));
        assertThat(combinedState.soundResult.state, is(SleepSoundsProcessor.SoundResult.State.OK));
        assertThat(combinedState.sleepSoundStatus.sound.get(), is(sounds.get(0)));
        assertThat(combinedState.sleepSoundStatus.duration.get(), is(durations.get(0)));
        assertThat(combinedState.sleepSoundStatus.isPlaying, is(true));
        assertThat(combinedState.sleepSoundStatus.volumePercent.get(), is(100));
    }
    // endregion getCombinedState
}