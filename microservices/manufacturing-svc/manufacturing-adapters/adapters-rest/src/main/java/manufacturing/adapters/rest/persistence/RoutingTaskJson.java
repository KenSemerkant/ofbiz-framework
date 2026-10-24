package manufacturing.adapters.rest.persistence;

import java.util.ArrayList;
import java.util.List;
import manufacturing.domain.Routing;
import tools.jackson.databind.ObjectMapper;

/** JSON mapping for the inline routing tasks. */
final class RoutingTaskJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record TaskDto(int seq, String name, long estimatedDuration) {
    }

    public static String serialize(List<Routing.Task> tasks) {
        return MAPPER.writeValueAsString(tasks.stream()
            .map(t -> new TaskDto(t.seq(), t.name(), t.estimatedDuration()))
            .toList());
    }

    public static List<Routing.Task> deserialize(String json) {
        List<TaskDto> dtos = MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(ArrayList.class, TaskDto.class));
        return dtos.stream()
            .map(dto -> new Routing.Task(dto.seq(), dto.name(), dto.estimatedDuration()))
            .toList();
    }

    private RoutingTaskJson() {
    }
}
