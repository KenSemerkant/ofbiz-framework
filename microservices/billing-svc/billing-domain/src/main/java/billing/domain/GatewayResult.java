package billing.domain;

/**
 * Gateway adapter outcome: approved carries the gateway reference number
 * for the follow-up capture/void; declined carries the gateway result code.
 */
public record GatewayResult(boolean approved, String refNum, String resultCode) {

    public static GatewayResult approved(String refNum) {
        return new GatewayResult(true, refNum, "APPROVED");
    }

    public static GatewayResult declined(String resultCode) {
        return new GatewayResult(false, null, resultCode);
    }
}
