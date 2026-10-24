package workeffort.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import workeffort.application.TimesheetService;
import workeffort.application.WorkEffortSerializer;
import workeffort.application.WorkEffortService;
import workeffort.application.WorkEffortStore;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class WorkeffortBeans {

    @Bean
    WorkEffortService workEffortService(WorkEffortStore efforts,
            workeffort.application.OutboxWriter outbox, WorkEffortSerializer serializer) {
        return new WorkEffortService(efforts, outbox, serializer);
    }

    @Bean
    TimesheetService timesheetService(workeffort.application.TimesheetStore timesheets,
            WorkEffortStore efforts, workeffort.application.OutboxWriter outbox,
            WorkEffortSerializer serializer) {
        return new TimesheetService(timesheets, efforts, outbox, serializer);
    }
}
