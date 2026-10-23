package billing.application;

import billing.domain.GatewayResult;
import java.math.BigDecimal;

/**
 * Payment-gateway adapter port (P5-F1-S2, replaces OFBiz
 * PaymentGatewayServices). Implementations carry the idempotency key on
 * every call so a retry can never double-charge; a hard outage surfaces as
 * {@link GatewayUnavailableException} and the caller routes it to the DLQ.
 */
public interface PaymentGateway {

    GatewayResult authorize(String idempotencyKey, BigDecimal amount, String currencyUomId);

    GatewayResult capture(String idempotencyKey, String refNum, BigDecimal amount);

    GatewayResult voidAuthorization(String idempotencyKey, String refNum);
}
