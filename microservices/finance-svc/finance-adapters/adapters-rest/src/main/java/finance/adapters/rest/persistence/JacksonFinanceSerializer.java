package finance.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import finance.application.FinanceSerializer;
import finance.domain.GlTransaction;
import finance.domain.Period;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the finance.* topics (Jackson 3). */
@Component
public class JacksonFinanceSerializer implements FinanceSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializeTransaction(GlTransaction transaction) {
        ObjectNode node = json.createObjectNode();
        node.put("acctgTransId", transaction.acctgTransId());
        node.put("transType", transaction.transType());
        node.put("periodId", transaction.periodId());
        node.put("posted", transaction.isPosted());
        node.put("entryCount", transaction.entries().size());
        return node.toString();
    }

    @Override
    public String serializePeriodClosed(Period period) {
        ObjectNode node = json.createObjectNode();
        node.put("periodId", period.periodId());
        node.put("closed", period.isClosed());
        node.put("thruDate", period.thruDate().toString());
        return node.toString();
    }
}
