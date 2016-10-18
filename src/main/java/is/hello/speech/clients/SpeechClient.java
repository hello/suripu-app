package is.hello.speech.clients;

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

import com.google.cloud.speech.v1beta1.AsyncRecognizeRequest;
import com.google.cloud.speech.v1beta1.AsyncRecognizeResponse;
import com.google.cloud.speech.v1beta1.RecognitionAudio;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.SpeechGrpc;
import com.google.cloud.speech.v1beta1.SpeechRecognitionResult;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognizeRequest;
import com.google.common.base.Optional;
import com.google.longrunning.GetOperationRequest;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import is.hello.speech.configuration.AudioConfiguration;
import is.hello.speech.models.SpeechServiceResult;
import is.hello.speech.utils.ClientUtils;
import is.hello.speech.utils.HelloStreamObserver;
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

    private static final long OPERATION_WAIT_TIME = 500L;

    private final AudioConfiguration configuration;

    private final ManagedChannel channel;

    private final SpeechGrpc.SpeechBlockingStub stub;
    private final OperationsGrpc.OperationsBlockingStub statusClient;

    private final SpeechGrpc.Speech stubStreaming;

    /**
     * Construct client connecting to Cloud Speech server at {@code host:port}.
     */
    public SpeechClient(String host, int port, AudioConfiguration configuration) throws IOException {
        this.configuration = configuration;

        channel = ClientUtils.createChannel(host, port);
        stub = SpeechGrpc.newBlockingStub(channel);
        statusClient = OperationsGrpc.newBlockingStub(channel);

        stubStreaming = SpeechGrpc.newStub(channel);

        LOGGER.info("action=created-stub host={} port={}", host, port);
    }

    public void shutdown() throws InterruptedException {
//        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        channel.shutdownNow();
    }

    /** Send async recognize requests to server. */
    public SpeechServiceResult recognize(final byte[] body, final int samplingRate) throws InterruptedException, IOException {

        SpeechServiceResult speechServiceResult = new SpeechServiceResult();

        final RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(ByteString.copyFrom(body)).build();
        LOGGER.info("action=sending-audio byte_size={}", audio.getContent().size());

        final RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(configuration.getEncoding()) // AudioEncoding.LINEAR16
                .setSampleRate(samplingRate)
                .setMaxAlternatives(1)
                .build();

        final AsyncRecognizeRequest request = AsyncRecognizeRequest.newBuilder()
                .setConfig(config)
                .setAudio(audio).build();

        Operation operation;
        Operation status;
        try {
            operation = stub.asyncRecognize(request);
            LOGGER.debug("action=async-recognize operation_handle={}", operation.getName());
        } catch (StatusRuntimeException e) {
            LOGGER.warn("error=rpc-fail status={} error_msg={}", e.getStatus(), e.getMessage());
            return speechServiceResult;
        }

        // loop till operations is done
        while (true) {
            try {
                LOGGER.info("action=wait-for-operations time={} unit=ms", OPERATION_WAIT_TIME);
                Thread.sleep(OPERATION_WAIT_TIME);
                GetOperationRequest operationRequest = GetOperationRequest.newBuilder().setName(operation.getName()).build();
                status = statusClient.getOperation(operationRequest);
                if (status.getDone()) {
                    break;
                }

            } catch (Exception ex) {
                LOGGER.warn("error=fail-to-get-operations result msg={}", ex.getMessage());
                return speechServiceResult;
            }
        }

        // operation done, get the results
        try {
            final AsyncRecognizeResponse asyncResponse = status.getResponse().unpack(AsyncRecognizeResponse.class);
            LOGGER.info("received_response={}", asyncResponse);
            final SpeechRecognitionResult speechRecognitionResult = asyncResponse.getResults(0);
            speechServiceResult.setFinal(true);
            speechServiceResult.setStability(1.0f);
            speechServiceResult.setTranscript(Optional.of(speechRecognitionResult.getAlternatives(0).getTranscript()));
            speechServiceResult.setConfidence(speechRecognitionResult.getAlternatives(0).getConfidence());
        } catch (com.google.protobuf.InvalidProtocolBufferException ex) {
            LOGGER.warn("error=protobuf-unpack-error, msg={}", ex.getMessage());
        }
        return speechServiceResult;
    }


    /**
     * Send StreamRecognizeRequest
     * @param bytes input stream buffer
     * @param samplingRate audio sampling rate
     * @return transcribed speech result
     * @throws InterruptedException
     * @throws IOException
     */
    public SpeechServiceResult stream(final byte [] bytes, int samplingRate) throws InterruptedException, IOException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        final HelloStreamObserver responseObserver = new HelloStreamObserver(finishLatch);
        final StreamObserver<StreamingRecognizeRequest> requestObserver = stubStreaming.streamingRecognize(responseObserver);
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

            LOGGER.debug("body_length={} buffer_size={} num_chunks={}", bytes.length, bufferSize, numChunks);

            for (int i = 0; i < numChunks + 1; i++) {
                final int startIndex = i * bufferSize;
                final int endIndex = (i == numChunks) ? bytes.length : startIndex + bufferSize;
                final byte[] buffer = Arrays.copyOfRange(bytes, startIndex, endIndex);

                totalBytes += buffer.length;
                LOGGER.debug("action=read-bytes-from-input-stream iteration={} bytes_read={}", i, buffer.length);

                final StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(ByteString.copyFrom(buffer))
                        .build();
                requestObserver.onNext(request);
            }
            LOGGER.info("action=sent-bytes-from-audio total_bytes={}", totalBytes);
        } catch (RuntimeException e) {
            // Cancel RPC.
            LOGGER.error("error=stream-audio-fail");
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