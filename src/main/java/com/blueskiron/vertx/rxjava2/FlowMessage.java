package com.blueskiron.vertx.rxjava2;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.reactivestreams.Subscription;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A control flow message.
 * 
 * @author (Juraj Zachar) juraj.zachar@gmail.com
 *
 */
interface FlowMessage {
  
  static final String TYPE_K = "type";
  static final String ID_K = "id";
  static final String SUBSCRIBER_ADDRESS_K = "subscriberAddress";
  static final String SOURCE_ADDRESS_K = "sourceAddress";
  static final String CONTROL_ADDRESS_K = "controlAddress";
  static final String SUBSCRIPTION_K = "subscription";
  static final String ONSUBSCRIBE_K = "onSuubscribe";
  static final String REQUEST_K = "request";
  static final String STACK_TRACE_DUMP_K = "stackTraceDump";

  /**
   * @return universal representation of this message.
   */
  JsonObject toJson();
  
  static FlowMessage fromJson(JsonObject json){
    String type = json.getString(TYPE_K);
    FlowMessage flowMessage = null;
    if(type.equals(EventBusSubscription.class.getName())){
      flowMessage = EventBusSubscription.fromJson(json);
    }
    if(type.equals(OnSubscribe.class.getName())){
      flowMessage = OnSubscribe.fromJson(json);
    }
    if(type.equals(Request.class.getName())){
      flowMessage = Request.fromJson(json);
    }
    if(type.equals(OnError.class.getName())){
      flowMessage = OnError.fromJson(json);
    }
    if(type.equals(OnComplete.class.getName())){
      flowMessage = OnComplete.fromJson(json);
    }
    if(type.equals(Cancel.class.getName())){
      flowMessage = Cancel.fromJson(json);
    }
    return flowMessage;
  }

  static class EventBusSubscription implements FlowMessage, Subscription {
    private final String id;
    private final OnSubscribe onSubscribe;
    private transient Vertx vertx;

    /**
     * @param sourceAddress
     * @param subscriberAddress
     */
    EventBusSubscription(String id, OnSubscribe onSubscribe) {
      this.id = id;
      this.onSubscribe = onSubscribe;
    }
    
    public EventBusSubscription setVertx(Vertx vertx) {
      this.vertx = vertx;
      return this;
    }
    
    public String id() {
      return id;
    }

    @Override
    public void request(long n) {
      if(vertx == null){
        return;
      }
      vertx.eventBus().send(onSubscribe.sourceAddress, new Request(id, n).toJson());
    }

    @Override
    public void cancel() {
      vertx.eventBus().send(onSubscribe.sourceAddress, new Cancel(id).toJson());

    }

    public JsonObject toJson() {
      return new JsonObject()
          .put(TYPE_K, EventBusSubscription.class.getName())
          .put(ID_K, id)
          .put(ONSUBSCRIBE_K, onSubscribe.toJson());
      }

    static EventBusSubscription fromJson(JsonObject json) {
      EventBusSubscription subscription = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(EventBusSubscription.class.getName())) {
        return subscription;
      }
      String id = json.getString(ID_K);
      if (id == null) {
        return subscription;
      }
      JsonObject onSubscribe = json.getJsonObject(ONSUBSCRIBE_K);
      if (onSubscribe == null) {
        return subscription;
      }
      subscription = new EventBusSubscription(id, OnSubscribe.fromJson(onSubscribe));
      return subscription;
    }

    public OnSubscribe getOnSubscribe() {
      return onSubscribe;
    }
  }

  static class OnSubscribe implements FlowMessage {

    private final String subscriberAddress;
    private final String sourceAddress;
    private final String controlAddress;

    public OnSubscribe(String subscriberAddress, String sourceAddress, String controlAddress) {
      this.sourceAddress = sourceAddress;
      this.subscriberAddress = subscriberAddress;
      this.controlAddress = controlAddress;
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject()
          .put(SUBSCRIBER_ADDRESS_K, subscriberAddress)
          .put(SOURCE_ADDRESS_K, sourceAddress)
          .put(CONTROL_ADDRESS_K, controlAddress)
          .put(TYPE_K, OnSubscribe.class.getName());
    }

    static OnSubscribe fromJson(JsonObject json) {
      OnSubscribe onSubscribe = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(OnSubscribe.class.getName())) {
        return onSubscribe;
      }

      String sourceAddress = json.getString(SOURCE_ADDRESS_K);
      if (sourceAddress == null) {
        return onSubscribe;
      }
      String subscriberAddress = json.getString(SUBSCRIBER_ADDRESS_K);
      if (subscriberAddress == null) {
        return onSubscribe;
      }
      String controlAddress = json.getString(CONTROL_ADDRESS_K);
      if (controlAddress == null) {
        return onSubscribe;
      }
      return new OnSubscribe(subscriberAddress, sourceAddress, controlAddress);
    }

    public String getSubscriberAddress() {
      return subscriberAddress;
    }

    public String getSourceAddress() {
      return sourceAddress;
    }

    public String getErrorAddress() {
      return controlAddress;
    }

  }

  static final class Request implements FlowMessage {
    private final String id;
    private final long request;

    public Request(String id, long request) {
      this.id = id;
      this.request = request;
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject().put(ID_K, id).put(REQUEST_K, request).put(TYPE_K, Request.class.getName());
    }

    static Request fromJson(JsonObject json) {
      Request request = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(Request.class.getName())) {
        return request;
      }
      String id = json.getString(ID_K);
      if (id == null) {
        return request;
      }
      long r = json.getLong(REQUEST_K);
      return new Request(id, r);
    }

    String getId() {
      return id;
    }

    long getRequest() {
      return request;
    }
  }

  static final class OnError extends Exception implements FlowMessage {
    private static final long serialVersionUID = 4895714631039420839L;

    private final String stackTraceDump;

    public OnError(String stackTraceDump) {
      this.stackTraceDump = stackTraceDump;
    }

    public OnError(Throwable e) {
      StringWriter errors = new StringWriter();
      e.printStackTrace(new PrintWriter(errors));
      stackTraceDump = errors.toString();
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject().put(STACK_TRACE_DUMP_K, stackTraceDump).put(TYPE_K, OnError.class.getName());
    }

    static OnError fromJson(JsonObject json) {
      OnError onError = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(OnError.class.getName())) {
        return onError;
      }
      String stackTraceDump = json.getString(STACK_TRACE_DUMP_K);
      if (stackTraceDump == null) {
        return onError;
      }
      return new OnError(stackTraceDump);
    }

  }
  
  static final class Cancel extends Exception implements FlowMessage {
    private static final long serialVersionUID = 4895714631039420839L;

    private final String id;

    public Cancel(String id) {
      this.id = id;
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject().put(ID_K, id).put(TYPE_K, Cancel.class.getName());
    }

    static Cancel fromJson(JsonObject json) {
     Cancel cancel = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(Cancel.class.getName())) {
        return cancel;
      }
      String id = json.getString(ID_K);
      if (id == null) {
        return cancel;
      }
      return new Cancel(id);
    }
    public String getId() {
      return id;
    }
  }
  
  static final class OnComplete extends Exception implements FlowMessage {

    private static final long serialVersionUID = -3398832979142088407L;
    private final String id;

    public OnComplete(String id) {
      this.id = id;
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject().put(ID_K, id).put(TYPE_K, OnComplete.class.getName());
    }

    static OnComplete fromJson(JsonObject json) {
      OnComplete onComplete = null;
      String type = json.getString(TYPE_K);
      if (!type.equals(OnComplete.class.getName())) {
        return onComplete;
      }
      String id = json.getString(ID_K);
      if (id == null) {
        return onComplete;
      }
      return new OnComplete(id);
    }

    public String getId() {
      return id;
    }

  }
}
