package com.blueskiron.vertx.rxjava2;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.blueskiron.vertx.rxjava2.FlowMessage.EventBusSubscription;
import com.blueskiron.vertx.rxjava2.FlowMessage.OnComplete;
import com.blueskiron.vertx.rxjava2.FlowMessage.OnError;
import com.blueskiron.vertx.rxjava2.FlowMessage.OnSubscribe;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * @author Juraj Zachar (juraj.zachar@gmail.com)
 *
 * @param <T>
 *          MarketData
 */
public class FlowableEventBusSubscriber<T> implements Publisher<T> {
	private final Vertx vertx;
	private final String sourceAddress;
	private final String subscriberAddress;
	private final String controlAddress;

	//volatile 
	private Subscriber<? super T> subscriber = null;
	
	/**
	 * @param bus
	 * @param feedAddress
	 */
	public FlowableEventBusSubscriber(Vertx vertx, String sourceAddress, String subscriberAddress) {
		this.vertx = vertx;
		this.subscriberAddress = subscriberAddress;
		this.sourceAddress = sourceAddress;
		this.controlAddress = sourceAddress + "/control";
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
	  if(subscriber == null){
	    return;
	  }
		OnSubscribe onSubscribe = new OnSubscribe(subscriberAddress, sourceAddress, controlAddress);
		vertx.eventBus().<JsonObject>send(sourceAddress, onSubscribe.toJson(), ar -> {
		  if(ar.failed()){
		    subscriber.onError(ar.cause());
		    return;
		  }
		  this.subscriber = subscriber;
		  JsonObject body = ar.result().body();
	    FlowMessage fm = FlowMessage.fromJson(body);
	    if(fm instanceof EventBusSubscription){
	      EventBusSubscription sub = (EventBusSubscription) fm;
	      sub.setVertx(vertx);
	      serve(sub);
	      subscriber.onSubscribe(sub);
	    }
		});
	}

  private void serve(EventBusSubscription sub) {
    //subscriber channel
    vertx.eventBus().<T>consumer(subscriberAddress, message -> {
      subscriber.onNext(message.body());
    });
    //control channel
    vertx.eventBus().<JsonObject>consumer(controlAddress, message -> {
      FlowMessage fm = FlowMessage.fromJson(message.body());
      if(fm instanceof OnError){
        subscriber.onError((Throwable) fm);
      } else if (fm instanceof OnComplete){
        subscriber.onComplete();
      }
    });
  }

}
