
[![Build Status](https://travis-ci.org/jurajzachar/vertx-rxjava2.svg?branch=master)](https://travis-ci.org/jurajzachar/vertx-rxjava2)

# Vert.X + RxJava 2
Interim solution to adapt Vert.X ReadStream to Rxjva2's Flowable. Use Vert.X EventBus to subscribe multiple subscribers to source Flowable.

# Dependencies

 * Vert.X 3.5.x
 * RxJava2 2.1.x(latest)

# Build
    mvn clean install

# Maven
    <dependency>
      <groupId>com.blueskiron</groupId>
      <artifactId>vertx-rxjava2</artifactId>
      <version>1.0.0</version>
    </dependency>

# Example: [FlowableReadStream](https://github.com/jurajzachar/vertx-rxjava2/blob/master/src/main/java/com/blueskiron/vertx/rxjava2/FlowableReadStream.java)
Simply wrap [ReadStream](http://reactivex.io/RxJava/2.x/javadoc/) and use normal [Flowable](http://reactivex.io/RxJava/2.x/javadoc/):

```java
FileSystem fs = getVertx().fileSystem();
  fs.open(FILEPATH.toAbsolutePath().toString(), new OpenOptions().setRead(true), ar -> {
    if (ar.failed()) {
      context.fail(ar.cause());
    } else {
      Flowable<Buffer> flowable = FlowableReadStream.newLineDelimitedReadStream(ar.result());
      flowable.subscribe(new PacedTestSubscriber(getVertx(), async, 1000));
    }
  });
```

# Example: [FlowableEventbusPublisher](https://github.com/jurajzachar/vertx-rxjava2/blob/master/src/main/java/com/blueskiron/vertx/rxjava2/FlowableEventBusPublisher.java) and [FlowableEventBusSubscriber](https://github.com/jurajzachar/vertx-rxjava2/blob/master/src/main/java/com/blueskiron/vertx/rxjava2/FlowableEventBusSubscriber.java)

```java
FileSystem fs = getVertx().fileSystem();
  fs.open(FILEPATH.toAbsolutePath().toString(), new OpenOptions().setRead(true), ar -> {
    if (ar.failed()) {
      context.fail(ar.cause());
    } else {
      Flowable<Buffer> flowable = FlowableReadStream.newLineDelimitedReadStream(ar.result());
      String sourceAddress = "testSourceAddress";
      new FlowableEventBusPublisher<>(flowable, getVertx(), sourceAddress, new DeliveryOptions());
      FlowableEventBusSubscriber<Buffer> busSubscriber = new FlowableEventBusSubscriber<>(getVertx(), sourceAddress, "testSubscriberAddress");
      busSubscriber.subscribe(new PacedTestSubscriber(getVertx(), async, 50));
    }
  });
  ```
