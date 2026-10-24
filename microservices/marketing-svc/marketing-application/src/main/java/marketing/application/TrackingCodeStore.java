package marketing.application;

import java.util.List;
import java.util.Optional;
import marketing.domain.TrackingCode;

/** Persistence port for tracking codes. */
public interface TrackingCodeStore {

    Optional<TrackingCode> find(String trackingCodeId);

    void save(TrackingCode trackingCode);

    List<TrackingCode> all();
}
