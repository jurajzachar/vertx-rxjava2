package test.com.blueskiron.vertx.rxjava2;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

public abstract class AbstractTest {
  static final Path FILEPATH = Paths.get(getCurrentWorkingDir(), "src", "test", "resources", "asyncFile.txt");
  private Vertx vertx;
  private final Scheduler scheduler = Schedulers.computation();
  
  @Before
  public void before(TestContext context) {
    Async async = context.async();
    this.vertx = Vertx.vertx(new VertxOptions()
        .setInternalBlockingPoolSize(4).setBlockedThreadCheckInterval(5000).setEventLoopPoolSize(4));
    async.complete();
  }

  @After
  public void after() {
    if (vertx != null) {
      vertx.close();
    }
    scheduler.shutdown();
  }
  
  public static String getCurrentWorkingDir() {
    Path currentRelativePath = Paths.get("");
    return currentRelativePath.toAbsolutePath().toString();
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public Vertx getVertx() {
    return vertx;
  }

  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }
}
