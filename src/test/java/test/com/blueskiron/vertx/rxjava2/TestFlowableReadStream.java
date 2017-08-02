package test.com.blueskiron.vertx.rxjava2;

import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueskiron.vertx.rxjava2.FlowableReadStream;

import io.reactivex.Flowable;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TestFlowableReadStream extends AbstractTest{
  
  private static final Logger LOG = LoggerFactory.getLogger(TestFlowableReadStream.class);
  
  @Test
  public void test(TestContext context) {
    Async async = context.async();
    context.assertTrue(FILEPATH.toFile().exists());
    FileSystem fs = getVertx().fileSystem();
    fs.open(FILEPATH.toAbsolutePath().toString(), new OpenOptions().setRead(true), ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        Flowable<Buffer> flowable = FlowableReadStream.newLineUnixDelimitedReadStream(ar.result());
        LOG.debug("Subcribing slow consumer to ReadStream<Buffer>...");
        flowable.subscribe(new PacedTestSubscriber(getVertx(), async, 1000));
      }
    });
  }
  
  static final class PacedTestSubscriber implements Subscriber<Buffer> {
    private final Vertx vertx;
    private final Async async;
    private final long delay;
    private Subscription sub;
    public PacedTestSubscriber(Vertx vertx, Async async, long delay) {
      this.vertx = vertx;
      this.delay = delay;
      this.async = async;
    }
    
    @Override
    public void onSubscribe(Subscription sub) {
      LOG.debug("Got subscription: " + sub.getClass().getName());
      this.sub = sub;
      delayedRequest();
    }

    @Override
    public void onNext(Buffer buf){
      String data = new String(buf.getBytes(), Charset.forName("UTF-8"));
      LOG.debug("Observed: {}", data);
      delayedRequest();
    }

    @Override
    public void onError(Throwable t) {
      LOG.error("{}", t);
    }

    @Override
    public void onComplete() {
      async.complete();
    }
    
    private void delayedRequest(){
      vertx.setTimer(delay, id -> {
        LOG.debug("Requesting one...");
        sub.request(1);
      });
    }
  }
}
