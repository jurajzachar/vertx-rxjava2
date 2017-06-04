package com.blueskiron.vertx.rxjava2;

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
 * @author (Juraj Zachar) juraj.zachar@gmail.com
 *
 * @param <T>
 */
public class FlowableReadStream<T, R> extends Flowable<R> {

	private ReadStreamSubscription<T, R> backing = null;

	private FlowableReadStream() {
	}

	/**
	 * Creates a {@link Flowable<Buffer>} from the underlying
	 * {@link ReadStream<Buffer>}.
	 * 
	 * @param bufferStream
	 * @param recordParser
	 *          buffer handler to apply before it's emitted as flowable stream.
	 * @return
	 */
	public static FlowableReadStream<Buffer, Buffer> bufferReadStream(ReadStream<Buffer> bufferStream,
	    RecordParser recordParser) {
		FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
		flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff, recordParser);
		return flowableStream;
	}
	
	/**
	 * Creates a {@link Flowable<Buffer>} from the underlying
	 * {@link ReadStream<Buffer>} and applies a delimited {@link RecordParser} to the stream before it's emitted
	 * as flowable stream.
	 * 
	 * @param bufferStream
	 
	 * @return
	 */
	public static FlowableReadStream<Buffer, Buffer> newLineDelimitedReadStream(ReadStream<Buffer> bufferStream) {
		FlowableReadStream<Buffer, Buffer> flowableStream = new FlowableReadStream<>();
		flowableStream.backing = new BufferReadStreamSubscription(bufferStream, buff -> buff);
		return flowableStream;
	}
	
	/**
	 * * Creates a {@link Flowable<R>} from the underlying
	 * {@link ReadStream<T>}, applying {@link Function<T, R>} to each emitted element by the stream.
	 * 
	 * @param stream
	 * @param mapper
	 * @return
	 */
	public static <T, R> FlowableReadStream<T, R> genericReadStream(ReadStream<T> stream, Function<T, R> mapper){
		FlowableReadStream<T, R> flowableStream = new FlowableReadStream<>();
		flowableStream.backing = new ReadStreamSubscription<>(stream, mapper);
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
	 *
	 */
	private static class BufferReadStreamSubscription extends ReadStreamSubscription<Buffer, Buffer> {

		transient RecordParser recordParser;

		/**
		 * @param stream
		 * @param mapper
		 * @param recordParser
		 */
		public BufferReadStreamSubscription(ReadStream<Buffer> stream, Function<Buffer, Buffer> mapper, RecordParser recordParser) {
			super(stream, mapper);
			this.recordParser = recordParser;
			recordParser.setOutput(this::handle);
		}

		/**
		 * @param stream
		 * @param function
		 */
		public BufferReadStreamSubscription(ReadStream<Buffer> stream, Function<Buffer, Buffer> function) {
			this(stream, function, createLineParser(data -> {}));
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
	 * @author (Juraj Zachar) juraj.zachar@gmail.com
	 *
	 * @param <T>
	 */
	private static class ReadStreamSubscription<T, R> extends AtomicLong
	    implements Handler<T>, Subscription, Subscriber<R> {
		private static final long serialVersionUID = 6961788438854551704L;
		private final Function<T, R> mapper;
		final ReadStream<T> stream;
		final UnicastProcessor<R> processor = UnicastProcessor.create();
		Subscriber<? super R> child;
		Subscription proxy;
		volatile boolean paused = true;

		/**
		 * @param stream
		 * @param mapper
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
			} else {
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

	}
	
	/**
	 * Creates a new line "\n" delimited  {@link RecordParser}.
	 * @param dataHandler
	 * @return
	 */
	public static final RecordParser createLineParser(Handler<Buffer> dataHandler) {
		return RecordParser.newDelimited("\n", dataHandler);
	}
}