package party.application;

import party.domain.Party;

/**
 * Serialization port: the application layer emits event payloads but stays
 * free of JSON framework types (ADR-007); the adapter layer supplies this.
 */
public interface PartySerializer {

    String serialize(Party party);
}
