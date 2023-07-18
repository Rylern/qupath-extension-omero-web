package qupath.lib.images.servers.omero.common.omero_entities.annotations.entities;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/**
 * An OMERO experimenter represents a person working on an OMERO entity.
 */
public class Experimenter {
    @SerializedName(value = "id") private int id;
    @SerializedName(value = "firstName") private String firstName;
    @SerializedName(value = "lastName") private String lastName;

    /**
     * @return the unique ID of this experimenter, or 0 if not found
     */
    public int getId() {
        return id;
    }

    /**
     * @return the full name (first name + last name) of this experimenter,
     * or an empty String if not found
     */
    public String getFullName() {
        String firstName = Objects.toString(this.firstName, "");
        String lastName = Objects.toString(this.lastName, "");

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        } else {
            return firstName + lastName;
        }
    }
}
