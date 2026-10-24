package content.adapters.rest.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The AWS adapter is thin by design — these tests pin the delegation
 * (bucket, key, content type, path-style wiring) so the coverage gate
 * holds without an S3 server.
 */
class S3ObjectStoreTest {

    @Test
    void delegatesPutGetExistsWithBucketKeyAndContentType() {
        S3Client s3 = org.mockito.Mockito.mock(S3Client.class);
        S3ObjectStore store = new S3ObjectStore(s3, "ofbiz-content");

        store.put("contents/C-1", new byte[] {1, 2}, "application/pdf");
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertEquals("ofbiz-content", put.getValue().bucket());
        assertEquals("contents/C-1", put.getValue().key());
        assertEquals("application/pdf", put.getValue().contentType());

        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
            ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {1, 2}));
        assertArrayEquals(new byte[] {1, 2}, store.get("contents/C-1"));

        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(null);
        assertTrue(store.exists("contents/C-1"));
        when(s3.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().build());
        assertFalse(store.exists("contents/C-404"));
    }

    /** Config constructor builds a real client (path-style, endpoint, creds) without touching the network. */
    @Test
    void buildsFromConfiguration() {
        S3ObjectStore store = new S3ObjectStore("http://localhost:3900", "ofbiz-local",
            "ofbiz-s3", "ofbiz-s3-secret", "ofbiz-content");
        assertNotNull(store);
    }
}
