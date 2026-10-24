package humanres.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import humanres.application.HumanresSerializer;
import humanres.application.OnboardingSagaStore.SagaRow;
import humanres.domain.EmplPosition;
import humanres.domain.Employment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Event payloads for the humanres.* topics (Jackson 3). */
@Component
public class JacksonHumanresSerializer implements HumanresSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializePosition(EmplPosition position) {
        ObjectNode node = json.createObjectNode();
        node.put("emplPositionId", position.emplPositionId());
        node.put("emplPositionTypeId", position.emplPositionTypeId());
        if (position.reportingToEmplPositionId() != null) {
            node.put("reportingToEmplPositionId", position.reportingToEmplPositionId());
        }
        node.put("statusId", position.statusId());
        return node.toString();
    }

    @Override
    public String serializeEmployment(Employment employment) {
        ObjectNode node = json.createObjectNode();
        node.put("employmentId", employment.employmentId());
        node.put("partyId", employment.partyId());
        node.put("emplPositionId", employment.emplPositionId());
        node.put("fromDate", employment.fromDate().toString());
        if (employment.thruDate() != null) {
            node.put("thruDate", employment.thruDate().toString());
        }
        return node.toString();
    }

    @Override
    public String serializeOnboardingCompleted(SagaRow saga, String employmentId) {
        ObjectNode node = json.createObjectNode();
        node.put("onboardingId", saga.onboardingId());
        node.put("partyId", saga.partyId());
        node.put("emplPositionId", saga.emplPositionId());
        node.put("employmentId", employmentId);
        return node.toString();
    }
}
