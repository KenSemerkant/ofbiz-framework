package audit.application;

/** Port: publish an alert to the notification context (Kafka behind it). */
public interface AlertPublisher {

    void publish(Alert alert);
}
