package is.hello.speech.utils;

import com.google.common.base.Optional;
import com.google.common.io.LittleEndianDataOutputStream;
import davaguine.jeq.core.EqualizerInputStream;
import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.util.ByteArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ksg on 8/2/16
 */
public class AudioUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioUtils.class);

    public static final int WAVE_HEADER_SIZE = 44;
    public static final float SENSE_SAMPLING_RATE = 16000.0f;
    public static final com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat WATSON_AUDIO_FORMAT =
            com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat.WAV;

    // for adding WAV headers to watson stream
    private static final int WATSON_WAVE_HEADER_SIZE = 8;      // The WAVE meta-data header size.
    private static final int WATSON_WAVE_SIZE_POS = 4;         // The WAVE meta-data size position.
    private static final int WATSON_WAVE_METADATA_POS = 74;    // The WAVE meta-data position in bytes.

    // MP3 settings
    private static final int MP3_BITRATE = 44;  // TODO: make this configurable
    private static final boolean USE_VBR = false;
    public static final int DEFAULT_SAMPLE_SIZE_BITS = 16;
    public static final int DEFAULT_FRAME_SIZE = 2;
    public static final AudioFormat.Encoding DEFAULT_ENCODING = AudioFormat.Encoding.PCM_SIGNED;

    // Equalization settings
    public static final int NUM_CHANNELS = 1;
    private static final int NUM_EQ_BANDS = 10;
    private static final boolean SIGNED_DATA_TRUE = true;
    public static final boolean BIG_ENDIAN_FALSE = false;
    private static final List<Float> EQUALIZED_VALUES = new ArrayList<Float>(NUM_EQ_BANDS) {{
        add(0, 10.0f);  // 32
        add(1, 10.0f);  // 64
        add(2, 8.15f);  // 125
        add(3, 6.98f);  // 250
        add(4, 2.03f);  // 500
        add(5, 1.05f);  // 1k
        add(6, 5.60f);  // 2k
        add(7, 12.9f);  // 4k
        add(8, 16.9f);  // 8k
        add(9, 17.0f);  // 11K
    }};

    // For mp3 conversion
    public final static AudioFormat DEFAULT_AUDIO_FORMAT = new AudioFormat(
            DEFAULT_ENCODING,
            SENSE_SAMPLING_RATE,
            DEFAULT_SAMPLE_SIZE_BITS,
            NUM_CHANNELS,
            DEFAULT_FRAME_SIZE,
            SENSE_SAMPLING_RATE,
            BIG_ENDIAN_FALSE);

    // For ADPCM Decoding
    final static int[] STEP_SIZE_TABLE = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
            19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
            130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
            2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
            5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
            15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };

    final static int[] INDEX_TABLE = {
            -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8,
    };


    public static class AudioBytes {
        public final byte [] bytes;
        public final int contentSize;
        public final Optional<AudioFormat> format;

        public AudioBytes(byte[] bytes, int contentSize, AudioFormat format) {
            this.bytes = bytes;
            this.contentSize = contentSize;
            this.format = Optional.fromNullable(format);
        }

        static AudioBytes empty() {
            return new AudioBytes(new byte[0], 0, null);
        }
    }

    /**
     * Note: from IBM WaveUtils
     * Re-writes the data size in the header(bytes 4-8) of the WAVE(.wav) input stream.<br>
     * It needs to be read in order to calculate the size.
     * @param inputStream the input stream
     */
    public static AudioBytes convertStreamToBytesWithWavHeader(final InputStream inputStream) {
        byte [] audioBytes;
        try {
            audioBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            LOGGER.warn("error=fail-to-convert-input-stream error_msg={}", e.getMessage());
            return AudioBytes.empty();
        }

        final int fileSize = audioBytes.length - WATSON_WAVE_HEADER_SIZE;

        writeInt(fileSize, audioBytes, WATSON_WAVE_SIZE_POS);
        writeInt(fileSize - WATSON_WAVE_HEADER_SIZE, audioBytes, WATSON_WAVE_METADATA_POS);

        return new AudioBytes(audioBytes, audioBytes.length, null);
    }

    /**
     * Down-sample audio to a different sampling rate
     * @param bytes raw audio bytes
     * @param targetSampleRate target sample rate
     * @return data in AudioBytes
     */
    public static AudioBytes downSampleAudio(final byte[] bytes, Optional<AudioFormat> optionalSourceFormat, final float targetSampleRate) {

        final Optional<AudioInputStream> optionalSourceStream = getAudioStream(bytes, optionalSourceFormat);
        if (!optionalSourceStream.isPresent()) {
            return AudioBytes.empty();
        }

        final AudioInputStream sourceStream = optionalSourceStream.get();
        final AudioFormat sourceFormat = sourceStream.getFormat();
        final AudioFormat targetFormat = new AudioFormat(
                sourceFormat.getEncoding(),
                targetSampleRate,
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                targetSampleRate,
                sourceFormat.isBigEndian()
        );

        final AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
        try {
            final byte[] convertedBytes = IOUtils.toByteArray(convertedStream);
            return new AudioBytes(convertedBytes, convertedBytes.length, targetFormat);
        } catch (IOException e) {
            LOGGER.error("error=fail-down-sample-convert-stream-to-bytes error_msg={}", e.getMessage());
            return AudioBytes.empty();
        }
    }

    /**
     * Equalize audio to Sense-specific profile
     * see:
     * https://github.com/whamtet/jeq
     * https://sourceforge.net/projects/jeq/
     * @param bytes raw audio bytes
     * @param optionalSourceFormat source audio format
     * @return data in AudioBytes
     */
    public static AudioBytes equalize(final byte[] bytes,  Optional<AudioFormat> optionalSourceFormat) {

        final Optional<AudioInputStream> optionalSourceStream = getAudioStream(bytes, optionalSourceFormat);
        if (!optionalSourceStream.isPresent()) {
            return AudioBytes.empty();
        }

        final AudioInputStream sourceStream = optionalSourceStream.get();
        final AudioFormat sourceFormat = sourceStream.getFormat();

        final EqualizerInputStream equalizer = new EqualizerInputStream(sourceStream,
                sourceFormat.getSampleRate(),
                NUM_CHANNELS,
                SIGNED_DATA_TRUE,
                sourceFormat.getSampleSizeInBits(),
                BIG_ENDIAN_FALSE,
                NUM_EQ_BANDS);

        for (int band = 0; band < NUM_EQ_BANDS; band++) {
            equalizer.getControls().setBandDbValue(band, 0, EQUALIZED_VALUES.get(band));
        }

        // grab output of equalized audio
        final int bufferSize = bytes.length;
        final ByteArrayBuffer output = new ByteArrayBuffer(bufferSize);
        try {
            byte[] buffer = new byte[bufferSize];
            int bytesRead = 0;
            while (bytesRead >= 0) {
                bytesRead = equalizer.read(buffer, 0, buffer.length);
                if (bytesRead >= 0) {
                    output.append(buffer, 0, bytesRead);
                }
            }
            LOGGER.debug("action=equalize-success bytes_processed={} original_size={}", output.length(), bufferSize);
        } catch (IOException e) {
            LOGGER.error("error=equalize-fail error_msg={}", e.getMessage());
            return AudioBytes.empty();
        }

        return new AudioBytes(output.buffer(), output.length(), sourceFormat);
    }

    /**
     * Convert PCM audio to MP3
     * @param pcm audio values in PCM
     * @return mp3 bytes
     */
    public static byte[] encodePcmToMp3(final AudioBytes pcm) {

        if (!pcm.format.isPresent()) {
            LOGGER.error("error=no-pcm-format-found-for-mp3-conversion");
            return new byte[0];
        }

        final AudioFormat sourceFormat = pcm.format.get();
        final AudioFormat inputFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                sourceFormat.getSampleSizeInBits(),
                sourceFormat.getChannels(),
                sourceFormat.getFrameSize(),
                sourceFormat.getFrameRate(),
                sourceFormat.isBigEndian()
        );

        LameEncoder encoder = new LameEncoder(inputFormat, MP3_BITRATE, MPEGMode.MONO, Lame.QUALITY_HIGHEST, USE_VBR);

        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        final byte[] buffer = new byte[encoder.getPCMBufferSize()];
        int bytesToTransfer = Math.min(buffer.length, pcm.contentSize);
        int bytesWritten;
        int currentPcmPosition = 0;
        while (0 < (bytesWritten = encoder.encodeBuffer(pcm.bytes, currentPcmPosition, bytesToTransfer, buffer))) {
            currentPcmPosition += bytesToTransfer;
            bytesToTransfer = Math.min(buffer.length, pcm.contentSize - currentPcmPosition);

            mp3.write(buffer, 0, bytesWritten);
        }

        encoder.close();
        return mp3.toByteArray();
    }

    /**
     * Writes an number into an array using 4 bytes
     * @param value the number to write
     * @param array the byte array
     * @param offset the offset
     */
    private static void writeInt(int value, byte[] array, int offset) {
        for (int i = 0; i < 4; i++) {
            array[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    /**
     * convert raw bytes to AudioInputStream
     * @param bytes raw bytes
     * @param optionalSourceFormat audio format
     * @return Optional AudioInputStream
     */
    private static Optional<AudioInputStream> getAudioStream(final byte[] bytes, Optional<AudioFormat> optionalSourceFormat) {

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        if (optionalSourceFormat.isPresent()) {
            final AudioInputStream sourceStream = new AudioInputStream(inputStream, optionalSourceFormat.get(), bytes.length);
            return Optional.of(sourceStream);
        }

        try {
            final AudioInputStream sourceStream = AudioSystem.getAudioInputStream(inputStream);
            return Optional.of(sourceStream);
        } catch (UnsupportedAudioFileException e) {
            LOGGER.error("error=fail-to-get-audio-stream reason=unsupported-audio-file error_msg={}", e.getMessage());

        } catch (IOException e) {
            LOGGER.error("error=fail-to-get-audio-stream reason=io-exception error_msg={}", e.getMessage());
        }
        return Optional.absent();
    }

    public static byte[] decodeADPShitMAudio(byte[] adpcm) throws IOException {
        {
            final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(adpcm));

            final int outputSize = (adpcm.length + 1) * 4;

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final LittleEndianDataOutputStream dataOutputStream = new LittleEndianDataOutputStream(outputStream);

            short valpred = 0;
            int index = 0;
            int step = STEP_SIZE_TABLE[0];

            boolean bufferstep = false;

            int sign;
            int vpdiff;

            int delta;
            int inPos = 0;

            int readValue = 0;
            for (int i = 0; i < adpcm.length * 2 - 1; i++) {

                // step 1
                final byte currentData = adpcm[inPos];

                if (bufferstep) {
                    delta = readValue & 0x0f;
                } else {
                    readValue = dataInputStream.readByte();
                    delta = (currentData >> 4) & 0x0f;
                    inPos++;
                }

                bufferstep = !bufferstep;

                // step 2
                index += INDEX_TABLE[delta];
                if (index < 0) index = 0;
                if (index > 88) index = 88;

                // step 3
                sign = delta & 8;
                delta = delta & 7;

                // step 4
                vpdiff = step >> 3;
                if ((delta & 4) != 0) vpdiff += step;
                if ((delta & 2) != 0) vpdiff += step >> 1;
                if ((delta & 1) != 0) vpdiff += step >> 2;

                if (sign != 0) {
                    valpred -= vpdiff;
                } else {
                    valpred += vpdiff;
                }

                // step 5 - clamp values
                if (valpred > 32767) {
                    valpred = 32767;
                } else if (valpred < -32768) {
                    valpred = -32768;
                }

                // step 6 - update step
                step = STEP_SIZE_TABLE[index];

                // step 7 - output value
                dataOutputStream.writeShort(valpred);
            }

            dataInputStream.close();
            return outputStream.toByteArray();

        }
    }
}
