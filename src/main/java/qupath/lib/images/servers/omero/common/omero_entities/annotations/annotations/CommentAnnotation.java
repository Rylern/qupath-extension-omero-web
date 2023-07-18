package qupath.lib.images.servers.omero.common.omero_entities.annotations.annotations;

import com.google.gson.annotations.SerializedName;
import qupath.lib.images.servers.omero.common.gui.UiUtilities;
import qupath.lib.images.servers.omero.common.omero_entities.annotations.Annotation;

import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Annotation containing a text comment
 */
public class CommentAnnotation extends Annotation {
    private static final ResourceBundle resources = UiUtilities.getResources();
    @SerializedName(value = "textValue") private String value;

    /**
     * @return a localized title for a comment annotation
     */
    public static String getTitle() {
        return resources.getString("Common.OmeroEntities.Annotation.Comment.title");
    }

    /**
     * Indicates if an annotation type refers to a comment annotation
     *
     * @param type  the annotation type
     * @return whether this annotation type refers to a comment annotation
     */
    public static boolean isOfType(String type) {
        return "CommentAnnotationI".equalsIgnoreCase(type) || "comment".equalsIgnoreCase(type);
    }

    /**
     * @return the actual comment of the annotation
     */
    public Optional<String> getValue() {
        return Optional.ofNullable(value);
    }
}
