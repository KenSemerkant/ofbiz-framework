package finance.application;

import java.util.List;
import finance.domain.PostingRule;

/** Parking lot for auto-postings that could not post (P5-F2-S2 AC). */
public interface ParkedPostingStore {

    void park(String eventType, String aggregateKey, String reason, String payloadJson);

    List<String> parkedReasons();
}
