package content.application;

import content.domain.Content;

/** Event-payload port: the application layer never touches Jackson. */
public interface ContentSerializer {

    String serialize(Content content);
}
