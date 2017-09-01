package com.blueskiron.vertx.rxjava2;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.blueskiron.vertx.rxjava2.FlowMessage;
import com.blueskiron.vertx.rxjava2.FlowMessage.Cancel;
import com.blueskiron.vertx.rxjava2.FlowMessage.Request;

import static com.blueskiron.vertx.rxjava2.FlowMessage.*;

import io.reactivex.Flowable;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.JsonObject;

/**
 * A reusable {@link MessageProducer} that exposes {@link Flowable} to
 * subscribed {@link MessageConsumer}.
 * 
 * @author Juraj Zachar (juraj.zachar@gmail.com)
 *
 * @param <T> type of stream.
 */
public class FlowableEventBusPublisher<T> {

  private final EventBus bus;
  private final String sourceAddress;
  private final Map<String, EventBusProxySubscriber<T>> proxies = new ConcurrentHashMap<>();
  private final Flowable<T> publisher;
  // optional delivery options if codec is not default...
  private DeliveryOptions deliveryOptions = new DeliveryOptions();

  /**
   * @param publisher the stream publisher.
   * @param vertx a vaild {@link Vertx} instance.
   * @param sourceAddress address which is used to forward stream to.
   */
  public FlowableEventBusPublisher(Flowable<T> publisher, Vertx vertx, String sourceAddress) {
    this.publisher = publisher.share();
    bus = vertx.eventBus();
    this.sourceAddress = sourceAddress;
    init();
  }

  /**
   * @param publisher the stream publisher.
   * @param vertx a vaild {@link Vertx} instance.
   * @param sourceAddress address which is used to forward stream to.
   * @param deliveryOptions Delivery options that are to be used for {@link EventBus} communication.
   */
  public FlowableEventBusPublisher(Flowable<T> publisher, Vertx vertx, String sourceAddress,
      DeliveryOptions deliveryOptions) {
    this.publisher = publisher.share();
    bus = vertx.eventBus();
    this.sourceAddress = sourceAddress;
    this.deliveryOptions = deliveryOptions;
    init();
  }

  private void init() {
    bus.<JsonObject>consumer(sourceAddress, this::handleFlowMessage);
  }

  private void handleFlowMessage(Message<JsonObject> message) {
    JsonObject body = message.body();
    FlowMessage fm = FlowMessage.fromJson(body);
    if (fm instanceof OnSubscribe) {
      handleOnSubscribe((OnSubscribe) fm, message);
    } else if (fm instanceof Request) {
      handleRequest((Request) fm);
    } else if(fm instanceof Cancel){
      handleCancel((Cancel) fm);
    }
  }

  private void handleCancel(Cancel fm) {
    String id = fm.getId();
    if(proxies.containsKey(id)){
      EventBusProxySubscriber<T> proxy = proxies.get(id);
      proxy.cancel();
      proxies.remove(id);
    }
  }

  private void handleRequest(Request fm) {
    String id = fm.getId();
    if(proxies.containsKey(id)){
      EventBusProxySubscriber<T> proxy = proxies.get(id);
      proxy.request(fm.getRequest());
    }
  }

  private void handleOnSubscribe(FlowMessage.OnSubscribe onSubscribe, Message<JsonObject> message) {
    String id = UUID.randomUUID().toString();
    EventBusSubscription child = new EventBusSubscription(id, onSubscribe);
    EventBusProxySubscriber<T> proxy = new EventBusProxySubscriber<>(bus, child, deliveryOptions);
    publisher.subscribe(proxy);
    proxies.put(id, proxy);
    message.reply(child.toJson());
  }

  /**
   * @param deliveryOptions Delivery options that are to be used for {@link EventBus} communication.
   * @return FlowableEventBusPublisher.
   */
  public FlowableEventBusPublisher<T> setDeliveryOptions(DeliveryOptions deliveryOptions) {
    this.deliveryOptions = deliveryOptions;
    return this;
  }

  private static class EventBusProxySubscriber<T> implements Subscriber<T> {
    private final EventBus bus;
    private final DeliveryOptions opts;
    private final EventBusSubscription child;
    private Subscription actual;

    EventBusProxySubscriber(EventBus bus, EventBusSubscription child, DeliveryOptions opts) {
      this.bus = bus;
      this.opts = opts;
      this.child = child;
    }

    private void request(long n) {
      if (actual != null && n > 0) {
        actual.request(n);
      }
    }

    private void cancel() {
      if (actual != null) {
        actual.cancel();
      }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      this.actual = subscription;
    }

    @Override
    public void onNext(T t) {
      bus.send(child.getOnSubscribe().getSubscriberAddress(), t, opts);
    }

    @Override
    public void onError(Throwable t) {
      bus.send(child.getOnSubscribe().getErrorAddress(), new OnError(t).toJson(), opts);
    }

    @Override
    public void onComplete() {
      bus.send(child.getOnSubscribe().getErrorAddress(), new OnComplete(child.id()).toJson(), opts);
    }

  }
}
