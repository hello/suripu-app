package is.hello.supichi.clients;

/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Client sends streaming audio to Speech.Recognize via gRPC and returns streaming transcription.
//
// Uses a service account for OAuth2 authentication, which you may obtain at
// https://console.developers.google.com
// API Manager > Google Cloud Speech API > Enable
// API Manager > Credentials > Create credentials > Service account key > New service account.
//
// Then set environment variable GOOGLE_APPLICATION_CREDENTIALS to the full path of that file.

import com.google.cloud.speech.spi.v1beta1.SpeechApi;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognizeRequest;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import is.hello.supichi.configuration.AudioConfiguration;
import is.hello.supichi.models.SpeechServiceResult;
import is.hello.supichi.utils.HelloStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client that sends streaming audio to Speech.Recognize and returns streaming transcript.
 */
public class SpeechClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechClient.class.getName());

    private final AudioConfiguration configuration;
    private final SpeechApi speechApi;

    /**
     * Construct client connecting to Cloud Speech server at {@code host:port}.
     */
    public SpeechClient(final AudioConfiguration configuration) throws IOException {
        this.configuration = configuration;
        this.speechApi  = SpeechApi.create();

        LOGGER.info("action=speech-api-create");
    }

    public void shutdown() throws Exception {
//        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        speechApi.close();
    }

    /**
     * Send StreamRecognizeRequest
     * @param bytes input stream buffer
     * @param samplingRate audio sampling rate
     * @return transcribed speech result
     * @throws InterruptedException
     * @throws IOException
     */
    public SpeechServiceResult stream(final String senseId, final byte [] bytes, int samplingRate) throws InterruptedException, IOException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        final HelloStreamObserver responseObserver = new HelloStreamObserver(finishLatch, senseId);
        final StreamObserver<StreamingRecognizeRequest> requestObserver = speechApi.streamingRecognizeCallable().bidiStreamingCall(responseObserver);
        try {
            // Build and send a RecognizeRequest containing the parameters for processing the audio.
            final RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(configuration.getEncoding())
                    .setSampleRate(samplingRate)
                    .setProfanityFilter(false)
                    .build();

            final StreamingRecognitionConfig streamConfig = StreamingRecognitionConfig.newBuilder()
                    .setConfig(config)
                    .setInterimResults(configuration.getInterimResultsPreference())
                    .setSingleUtterance(true)
                    .build();

            final StreamingRecognizeRequest firstRequest = StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamConfig).build();
            requestObserver.onNext(firstRequest);

            // Open audio file. Read and send sequential buffers of audio as additional RecognizeRequests.
            // FileInputStream in = new FileInputStream(new File(file));
            // For LINEAR16 at 16000 Hz sample rate, 3200 bytes corresponds to 100 milliseconds of audio.
            final int bufferSize = configuration.getBufferSize();
            final int numChunks = bytes.length / bufferSize;

            int totalBytes = 0;

            LOGGER.debug("sense_id={} body_length={} buffer_size={} num_chunks={}",senseId, bytes.length, bufferSize, numChunks);

            for (int i = 0; i < numChunks + 1; i++) {
                final int startIndex = i * bufferSize;
                final int endIndex = (i == numChunks) ? bytes.length : startIndex + bufferSize;
                final byte[] buffer = Arrays.copyOfRange(bytes, startIndex, endIndex);

                totalBytes += buffer.length;
                final StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(buffer))
                        .build();
                requestObserver.onNext(request);
            }
            LOGGER.info("action=sent-bytes-from-audio total_bytes={} sense_id={}",totalBytes, senseId);
        } catch (RuntimeException e) {
            // Cancel RPC.
            LOGGER.error("error=stream-audio-fail sense_id={}", senseId);
            requestObserver.onError(e);
            throw e;
        }

        // Mark the end of requests.
        requestObserver.onCompleted();

        // Receiving happens asynchronously.
        finishLatch.await(10, TimeUnit.SECONDS);

       // in.close(); // do we need to close this ourselves?
        return responseObserver.result();
    }
}