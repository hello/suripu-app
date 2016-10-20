package is.hello.supichi.cli;

import com.google.api.client.util.ByteStreams;
import com.google.api.client.util.Maps;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import is.hello.supichi.configuration.SpeechConfiguration;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by ksg on 6/27/16
 */
public class WatsonTextToSpeech extends ConfiguredCommand<SpeechConfiguration>{

    public WatsonTextToSpeech() {
        super("watson", "watson text-to-speech service");
    }

    @Override
    protected void run(Bootstrap<SpeechConfiguration> bootstrap, Namespace namespace, SpeechConfiguration speechConfiguration) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        final String username = "ce487881-c6f6-45cf-8aa5-585554de4941";
        final String password = "8riFzRbKOd4c";
        final String api = "https://stream.watsonplatform.net/text-to-speech/api";

        final TextToSpeech watson = new TextToSpeech();
        watson.setUsernameAndPassword(username, password);
        final Map<String, String> headers = Maps.newHashMap();
        headers.put("X-Watson-Learning-Opt-Out", "true");
        watson.setDefaultHeaders(headers);

         final List<Voice> voices = watson.getVoices().execute();
         System.out.println(voices);

        final Voice voice = watson.getVoice("en-US_AllisonVoice").execute();

        final InputStream stream = watson.synthesize("set my timer to 9:05am", voice, AudioFormat.WAV).execute();
        final File file = new File("/Users/kingshy/DEV/Hello/supichi/test.wav");
        ByteStreams.copy(stream, new FileOutputStream(file));
    }
}
