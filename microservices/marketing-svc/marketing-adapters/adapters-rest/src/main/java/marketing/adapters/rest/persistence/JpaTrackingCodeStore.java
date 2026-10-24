package marketing.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import marketing.application.TrackingCodeStore;
import marketing.domain.TrackingCode;
import org.springframework.stereotype.Repository;

/** JPA adapter for the TrackingCodeStore port. */
@Repository
public class JpaTrackingCodeStore implements TrackingCodeStore {

    private final TrackingCodeRepository trackingCodes;

    public JpaTrackingCodeStore(TrackingCodeRepository trackingCodes) {
        this.trackingCodes = trackingCodes;
    }

    @Override
    public Optional<TrackingCode> find(String trackingCodeId) {
        return trackingCodes.findById(trackingCodeId).map(this::toDomain);
    }

    @Override
    public void save(TrackingCode trackingCode) {
        trackingCodes.save(new TrackingCodeEntity(trackingCode.trackingCodeId(),
            trackingCode.marketingCampaignId(), trackingCode.description(),
            trackingCode.fromDate(), trackingCode.thruDate()));
    }

    @Override
    public List<TrackingCode> all() {
        return trackingCodes.findAllByOrderByTrackingCodeIdAsc().stream().map(this::toDomain)
            .toList();
    }

    private TrackingCode toDomain(TrackingCodeEntity entity) {
        return new TrackingCode(entity.getTrackingCodeId(), entity.getMarketingCampaignId(),
            entity.getDescription(), entity.getFromDate(), entity.getThruDate());
    }
}
