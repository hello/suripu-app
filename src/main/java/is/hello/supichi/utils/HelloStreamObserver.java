package is.hello.supichi.utils;

import com.google.cloud.speech.v1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.common.base.Optional;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import is.hello.supichi.clients.SpeechClient;
import is.hello.supichi.models.ResultGetter;
import is.hello.supichi.models.SpeechServiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class HelloStreamObserver implements StreamObserver<StreamingRecognizeResponse>, ResultGetter {

    private static final Logger logger =
            LoggerFactory.getLogger(SpeechClient.class.getName());

    private SpeechServiceResult speechServiceResult = new SpeechServiceResult();


    private final CountDownLatch finishLatch;
    private final String senseId;

    public HelloStreamObserver(final CountDownLatch latch, final String senseId) {
        this.finishLatch = latch;
        this.senseId = senseId;
    }

    @Override
    public void onNext(final StreamingRecognizeResponse response) {
        logger.debug("action=check-results size={} sense_id={}", response.getResultsCount(), senseId);

        for(final StreamingRecognitionResult result : response.getResultsList()) {
            speechServiceResult.setStability(result.getStability());
            speechServiceResult.setConfidence(result.getAlternatives(0).getConfidence());
            speechServiceResult.setTranscript(Optional.of(result.getAlternatives(0).getTranscript()));

            if(result.getIsFinal()) {
                speechServiceResult.setFinal(true);
                finishLatch.countDown();
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Status status = Status.fromThrowable(throwable);
        logger.warn("warning=stream-recognize-failed status={} sense_id={}", status, senseId);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        logger.info("action=stream-recognize-completed sense_id={}", senseId);
        finishLatch.countDown();
    }

    @Override
    public SpeechServiceResult result() {
        return speechServiceResult;
    }
}
