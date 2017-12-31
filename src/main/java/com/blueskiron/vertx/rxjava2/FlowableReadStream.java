package com.blueskiron.vertx.rxjava2;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.processors.UnicastProcessor;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;

/**
 * @param <T> type of {@link ReadStream}.
 * @param <R> type of {@link Flowable}.
 * 
 * @author (Juraj Zachar) juraj.zachar@gmail.com
 */
public class FlowableReadStream<T, R> extends Flowable<R> {

  private ReadStreamSubscription<T, R> backing = null;

  private FlowableReadStream() {
  }

  /**
   * Creates {@link Flowable} from the underlying {@link ReadStream}.
   * 
   * @param bufferStream the underlying {@link Buffer} stream.
   * @param recordParser a {@link RecordParser} used to process the stream.
   * @return a {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static FlowableReadStream<Buffer, Buffer> bufferReadStream(
      ReadStream<Buffer> bufferStream,
      RecordParser recordParser) {
    FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff, recordParser);
    return flowableStream;
  }

  /**
   * Creates {@link Flowable} from the underlying {@link ReadStream} and applies
   * OS-independent new line {@link RecordParser} to the stream before it's emitted as Flowable
   * stream.
   * 
   * @param bufferStream the underlying {@link Buffer} stream.
   * @return a {@link Buffer} {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static FlowableReadStream<Buffer, Buffer> newLineDelimitedReadStream(ReadStream<Buffer> bufferStream) {
    FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff, createLineParser(data -> {
    }));
    return flowableStream;
  }

  /**
   * Creates a {@link Flowable} from the underlying {@link ReadStream} and applies
   * UNIX new line {@link RecordParser} to the stream before it's emitted as Flowable stream.
   * 
   * @param bufferStream the underlying {@link Buffer} stream.
   * @return a {@link Buffer} {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static FlowableReadStream<Buffer, Buffer> newLineUnixDelimitedReadStream(ReadStream<Buffer> bufferStream) {
    FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff,
        createLineParser("\n", data -> {
        }));
    return flowableStream;
  }

  /**
   * Creates a {@link Flowable} from the underlying {@link ReadStream} and applies
   * Windows new line {@link RecordParser} to the stream before it's emitted as flowable stream.
   * 
   * @param bufferStream the underlying {@link Buffer} stream.
   * @return a {@link Buffer} {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static FlowableReadStream<Buffer, Buffer> newLineWindowsDelimitedReadStream(ReadStream<Buffer> bufferStream) {
    FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff,
        createLineParser("\r\n", data -> {
        }));
    return flowableStream;
  }

  /**
   * Creates a {@link Flowable} from the underlying {@link ReadStream}, applying
   * {@link Function} which is applied to each emitted element of the stream.
   * 
   * @param stream the underlying {@link ReadStream}.
   * @param mapper a mapper function that is used when processing the stream.
   * @return a generic {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static <T, R> FlowableReadStream<T, R> genericReadStream(ReadStream<T> stream, Function<T, R> mapper) {
    FlowableReadStream<T, R> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new ReadStreamSubscription<>(stream, mapper);
    return flowableStream;
  }

  /**
   * Creates a {@link Flowable} from the underlying {@link ReadStream}, applying
   * {@link Function} which is applied to each emitted element of the stream.
   * 
   * @param stream the underlying {@link ReadStream}.
   * @return a generic identity {@link Flowable} that is backed by {@link ReadStream}.
   */
  public static <T> FlowableReadStream<T, T> genericReadStream(ReadStream<T> stream) {
    FlowableReadStream<T, T> flowableStream = new FlowableReadStream<>();
    flowableStream.backing = new ReadStreamSubscription<>(stream, t -> t);
    return flowableStream;
  }

  @Override
  protected void subscribeActual(Subscriber<? super R> subscriber) {
    if (backing.child != null) {
      throw new IllegalStateException("ReadStream already subscribed!");
    }
    subscriber.onSubscribe(backing.bridge(subscriber));
  }

  /**
   * @author (Juraj Zachar) juraj.zachar@gmail.com
   */
  private static class BufferReadStreamSubscription extends ReadStreamSubscription<Buffer, Buffer> {

    transient RecordParser recordParser;

    /**
     * @param stream the underlying {@link ReadStream}.
     * @param mapper a mapper function that is used when processing the stream.
     * @param recordParser a {@link RecordParser} used to process the stream.
     */
    public BufferReadStreamSubscription(ReadStream<Buffer> stream, Function<Buffer, Buffer> mapper,
        RecordParser recordParser) {
      super(stream, mapper);
      this.recordParser = recordParser;
      recordParser.setOutput(this::handle);
    }

    private static final long serialVersionUID = 8931185648263744473L;

    @Override
    public void onSubscribe(Subscription subscription) {
      stream.resume();
      stream.handler(recordParser);
      stream.exceptionHandler(error -> onError(error));
      stream.endHandler(Void -> processor.onComplete());
      this.proxy = subscription;
    }

  }

  /**
   * A backing {@link Subscription} for {@link FlowableReadStream}.
   * @param <T> input type.
   * @param <R> output type.
   *
   * @author (Juraj Zachar) juraj.zachar@gmail.com
   */
  private static class ReadStreamSubscription<T, R> extends AtomicLong
      implements Handler<T>, Subscription, Subscriber<R> {
    private static final long serialVersionUID = 6961788438854551704L;
    private final Function<T, R> mapper;
    protected final ReadStream<T> stream;
    protected final UnicastProcessor<R> processor = UnicastProcessor.create();
    private Subscriber<? super R> child;
    protected Subscription proxy;
    private volatile boolean paused = true;

    /**
     * @param stream the underlying {@link ReadStream}.
     * @param mapper a mapper function that is used when processing the stream.
     */
    ReadStreamSubscription(ReadStream<T> stream, Function<T, R> mapper) {
      stream.pause();
      this.stream = stream;
      this.mapper = mapper;
    }

    private Subscription bridge(Subscriber<? super R> child) {
      this.child = child;
      processor.subscribe(this);
      return this;
    }

    private void exceptionHandler(Throwable error) {
      processor.onError(error);
    }

    private void pauseStream() {
      paused = true;
      stream.pause();
    }

    private void resumeStream() {
      paused = false;
      stream.resume();
    }

    @Override
    public void handle(T event) {
      if (event != null) {
        processor.onNext(mapper.apply(event));
      }
    }

    @Override
    public void request(long n) {
      long requested = n;
      if (n == Long.MAX_VALUE) {
        set(n);
      }
      else {
        requested = addAndGet(n);
      }
      if (requested > 0) {
        this.proxy.request(requested);
      }
      if (get() > 0 && paused) {
        resumeStream();
      }
    }

    @Override
    public void cancel() {
      pauseStream();
      // close stream and release resources?
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      stream.resume();
      stream.handler(this);
      stream.exceptionHandler(this::exceptionHandler);
      stream.endHandler(Void -> processor.onComplete());
      this.proxy = subscription;
    }

    @Override
    public void onNext(R event) {
      if (addAndGet(-1) <= 0) {
        pauseStream();
      }
      child.onNext(event);
    }

    @Override
    public void onError(Throwable error) {
      child.onError(error);
    }

    @Override
    public void onComplete() {
      child.onComplete();
    }
    
    //shut up findbugs and problems with non-transient / non-serializable fields...
    private void writeObject(ObjectOutputStream stream)
        throws IOException {
    stream.defaultWriteObject();
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    }
  }

  /**
   * Creates a {@link RecordParser} that delimits with the given end of line symbol
   * 
   * @param endOfLineSymbol system-specific EOL symbol.
   * @param dataHandler a {@link Handler} for the incoming {@link Buffer}.
   * @return a valid {@link RecordParser}.
   */
  public static final RecordParser createLineParser(String endOfLineSymbol, Handler<Buffer> dataHandler) {
    return RecordParser.newDelimited(endOfLineSymbol, dataHandler);
  }

  /**
   * OS-independent line parser. See {@link System#lineSeparator()} for more details.
   * 
   * @param dataHandler a {@link Handler} for the incoming {@link Buffer}.
   * @return a valid {@link RecordParser}.
   */
  public static final RecordParser createLineParser(Handler<Buffer> dataHandler) {
    return RecordParser.newDelimited(System.lineSeparator(), dataHandler);
  }
}