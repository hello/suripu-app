package is.hello.speech.utils;

import com.google.cloud.speech.v1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.common.base.Optional;
import com.google.protobuf.TextFormat;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import is.hello.speech.clients.SpeechClient;
import is.hello.speech.models.ResultGetter;
import is.hello.speech.models.SpeechServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class HelloStreamObserver implements StreamObserver<StreamingRecognizeResponse>, ResultGetter {

    private static final Logger logger =
            LoggerFactory.getLogger(SpeechClient.class.getName());

    private SpeechServiceResult speechServiceResult = new SpeechServiceResult();


    private final CountDownLatch finishLatch;

    public HelloStreamObserver(final CountDownLatch latch) {
        this.finishLatch = latch;
    }

    @Override
    public void onNext(final StreamingRecognizeResponse response) {
        logger.debug("action=check-results size={}", response.getResultsCount());

        for(final StreamingRecognitionResult result : response.getResultsList()) {
            logger.info("action=received-api-result result={}", TextFormat.printToString(result));
            speechServiceResult.setStability(result.getStability());
            speechServiceResult.setConfidence(result.getAlternatives(0).getConfidence());
            speechServiceResult.setTranscript(Optional.of(result.getAlternatives(0).getTranscript()));
            logger.debug("action=get-interim-api-result result={}", speechServiceResult);

            if(result.getIsFinal()) {
                speechServiceResult.setFinal(true);
                logger.info("action=get-final-result result={}", speechServiceResult);
                finishLatch.countDown();
            }

        }
    }

    @Override
    public void onError(Throwable throwable) {
        Status status = Status.fromThrowable(throwable);
        logger.warn("warning=stream-recognize-failed status={}", status);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        logger.info("action=stream-recognize-completed");
        finishLatch.countDown();
    }

    @Override
    public SpeechServiceResult result() {
        return speechServiceResult;
    }
}
