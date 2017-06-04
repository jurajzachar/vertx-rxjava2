package test.com.blueskiron.vertx.rxjava2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueskiron.vertx.rxjava2.FlowableEventBusPublisher;
import com.blueskiron.vertx.rxjava2.FlowableEventBusSubscriber;
import com.blueskiron.vertx.rxjava2.FlowableReadStream;

import io.reactivex.Flowable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import test.com.blueskiron.vertx.rxjava2.TestFlowableReadStream.PacedTestSubscriber;

@RunWith(VertxUnitRunner.class)
public class TestFlowableEventBusPublisher extends AbstractTest {

  private static final Logger LOG = LoggerFactory.getLogger(TestFlowableEventBusPublisher.class);
  
  @Test
  public void test(TestContext context) {
    Async async = context.async();
    context.assertTrue(FILEPATH.toFile().exists());
    FileSystem fs = getVertx().fileSystem();
    fs.open(FILEPATH.toAbsolutePath().toString(), new OpenOptions().setRead(true), ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
      } else {
        Flowable<Buffer> flowable = FlowableReadStream.newLineDelimitedReadStream(ar.result());
        String sourceAddress = "testSourceAddress";
        new FlowableEventBusPublisher<>(flowable, getVertx(), sourceAddress, new DeliveryOptions());
        FlowableEventBusSubscriber<Buffer> busSubscriber = new FlowableEventBusSubscriber<>(getVertx(), sourceAddress, "testSubscriberAddress");
        LOG.debug("Subcribing fast consumer to ReadStream<Buffer> via EventBus...");
        busSubscriber.subscribe(new PacedTestSubscriber(getVertx(), async, 50));
      }
    });
  }
  
}
