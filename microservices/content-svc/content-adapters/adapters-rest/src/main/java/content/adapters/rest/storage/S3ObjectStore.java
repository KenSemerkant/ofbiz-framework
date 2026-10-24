package content.adapters.rest.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import content.application.ObjectStore;

/**
 * S3-API adapter for the ObjectStore port (ADR-008): Garage locally,
 * cloud S3 = endpoint+creds swap. Path-style access — Garage (and most
 * on-prem gateways) does not do virtual-host style.
 */
@Component
public class S3ObjectStore implements ObjectStore {

    private final S3Client s3;
    private final String bucket;

    @Autowired
    public S3ObjectStore(@Value("${content.s3.endpoint}") String endpoint,
            @Value("${content.s3.region}") String region,
            @Value("${content.s3.access-key}") String accessKey,
            @Value("${content.s3.secret-key}") String secretKey,
            @Value("${content.s3.bucket}") String bucket) {
        this(S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            .build(), bucket);
    }

    S3ObjectStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
            .contentType(contentType).build(), RequestBody.fromBytes(bytes));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
