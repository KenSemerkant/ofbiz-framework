package order.application;

/**
 * Outbound port (P4-F2-S1 saga step): is this a party order-svc can accept
 * an order from? Adapter calls party-svc.
 */
public interface PartyValidator {

    boolean isValid(String partyId);
}
