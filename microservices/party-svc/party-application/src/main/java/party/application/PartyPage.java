package party.application;

import java.util.List;

/**
 * P2-F1-S3 pagination envelope. FUTURESTATE docs carry no explicit §8
 * pagination shape, so this is the program-wide envelope: items/page/size/
 * totalElements/totalPages (matched by later list endpoints).
 */
public record PartyPage(List<PartySummary> items, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public record PartySummary(String partyId, String kind, String displayName) {
    }
}
