package workeffort.adapters.rest.persistence;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import workeffort.application.WorkEffortSerializer;
import workeffort.domain.Timesheet;
import workeffort.domain.WorkEffort;

/** Event payloads for the workeffort.* topics (Jackson 3). */
@Component
public class JacksonWorkEffortSerializer implements WorkEffortSerializer {

    private final ObjectMapper json = new ObjectMapper();

    @Override
    public String serializeEffort(WorkEffort effort) {
        ObjectNode node = json.createObjectNode();
        node.put("workEffortId", effort.workEffortId());
        node.put("workEffortTypeId", effort.workEffortTypeId().name());
        node.put("workEffortName", effort.workEffortName());
        if (effort.parentWorkEffortId() != null) {
            node.put("parentWorkEffortId", effort.parentWorkEffortId());
        }
        node.put("statusId", effort.statusId().name());
        if (effort.estimatedStartDate() != null) {
            node.put("estimatedStartDate", effort.estimatedStartDate().toString());
        }
        if (effort.estimatedCompletionDate() != null) {
            node.put("estimatedCompletionDate", effort.estimatedCompletionDate().toString());
        }
        return node.toString();
    }

    @Override
    public String serializeTimesheetSubmitted(Timesheet timesheet) {
        ObjectNode node = json.createObjectNode();
        node.put("timesheetId", timesheet.timesheetId());
        node.put("partyId", timesheet.partyId());
        node.put("fromDate", timesheet.fromDate().toString());
        node.put("thruDate", timesheet.thruDate().toString());
        node.put("statusId", timesheet.statusId().name());
        node.put("entryCount", timesheet.entries().size());
        return node.toString();
    }
}
