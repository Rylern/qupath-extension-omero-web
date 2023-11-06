package qupath.ext.omero.core.entities.annotations.annotationsentities;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * An OMERO experimenter represents a person working on an OMERO entity.
 */
public class Experimenter {

    @SerializedName(value = "id") private int id;
    @SerializedName(value = "firstName") private String firstName;
    @SerializedName(value = "lastName") private String lastName;

    @Override
    public String toString() {
        return String.format("Experimenter %s with ID %d", getFullName(), id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Experimenter experimenter))
            return false;
        return experimenter.id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    /**
     * @return the unique ID of this experimenter, or 0 if not found
     */
    public int getId() {
        return id;
    }

    /**
     * @return the first name of this experimenter, or an empty String if not found
     */
    public String getFirstName() {
        return Objects.toString(firstName, "");
    }

    /**
     * @return the last name of this experimenter, or an empty String if not found
     */
    public String getLastName() {
        return Objects.toString(lastName, "");
    }

    /**
     * @return the full name (first name last name) of this experimenter,
     * or an empty String if not found
     */
    public String getFullName() {
        String firstName = getFirstName();
        String lastName = getLastName();

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else {
            return firstName + lastName;
        }
    }
}
