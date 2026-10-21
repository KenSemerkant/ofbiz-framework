package party.domain;

/** Natural-person name (OFBiz Person slice). */
public record Person(String firstName, String lastName) {

    public Person {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("firstName must not be blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
    }

    public String displayName() {
        return firstName + " " + lastName;
    }
}
