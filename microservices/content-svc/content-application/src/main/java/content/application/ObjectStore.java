package content.application;

/**
 * Object-storage port (ADR-008: S3 API only — Garage locally, S3 in the
 * cloud; the adapter is an endpoint+creds swap). The application layer
 * stays pure and never sees the AWS SDK.
 */
public interface ObjectStore {

    void put(String key, byte[] bytes, String contentType);

    byte[] get(String key);

    boolean exists(String key);
}
