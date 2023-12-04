package qupath.ext.omero.core.entities.repositoryentities.serverentities.image;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.ext.omero.gui.UiUtilities;
import qupath.ext.omero.core.entities.repositoryentities.RepositoryEntity;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;

import java.util.*;

/**
 * <p>
 *     Provides some information on an OMERO image.
 * </p>
 * <p>
 *     This class uses the {@link PixelInfo} class to get information about pixels.
 * </p>
 */
public class Image extends ServerEntity {

    private static final ResourceBundle resources = UiUtilities.getResources();
    private static final String[] ATTRIBUTES = new String[] {
            resources.getString("Web.Entities.Image.name"),
            resources.getString("Web.Entities.Image.id"),
            resources.getString("Web.Entities.Image.owner"),
            resources.getString("Web.Entities.Image.group"),
            resources.getString("Web.Entities.Image.acquisitionDate"),
            resources.getString("Web.Entities.Image.imageWidth"),
            resources.getString("Web.Entities.Image.imageHeight"),
            resources.getString("Web.Entities.Image.uncompressedSize"),
            resources.getString("Web.Entities.Image.nbZSlices"),
            resources.getString("Web.Entities.Image.nbChannels"),
            resources.getString("Web.Entities.Image.nbTimePoints"),
            resources.getString("Web.Entities.Image.pixelSizeX"),
            resources.getString("Web.Entities.Image.pixelSizeY"),
            resources.getString("Web.Entities.Image.pixelSizeZ"),
            resources.getString("Web.Entities.Image.pixelType")
    };
    private transient EnumSet<UNSUPPORTED_REASON> unsupportedReasons;
    private transient BooleanProperty isSupported;
    @SerializedName(value = "AcquisitionDate") private long acquisitionDate;
    @SerializedName(value = "Pixels") private PixelInfo pixels;
    public enum UNSUPPORTED_REASON {
        NUMBER_OF_CHANNELS,
        PIXEL_TYPE,
        PIXEL_API_UNAVAILABLE
    }

    /**
     * Creates an empty image only defined by its ID.
     */
    public Image(long id) {
        this.id = id;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public ObservableList<? extends RepositoryEntity> getChildren() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public ReadOnlyStringProperty getLabel() {
        return new SimpleStringProperty(name == null || name.isEmpty() ? "-" : name);
    }

    @Override
    public boolean isPopulatingChildren() {
        return false;
    }

    @Override
    public String getAttributeName(int informationIndex) {
        if (informationIndex < ATTRIBUTES.length) {
            return ATTRIBUTES[informationIndex];
        } else {
            return "";
        }
    }

    @Override
    public String getAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> name == null || name.isEmpty() ? "-" : name;
            case 1 -> String.valueOf(getId());
            case 2 -> getOwner().getFullName();
            case 3 -> getGroup().getName();
            case 4 -> acquisitionDate == 0 ? "-" : new Date(acquisitionDate).toString();
            case 5 -> getImageDimensions().map(d -> d[0] + " px").orElse("-");
            case 6 -> getImageDimensions().map(d -> d[1] + " px").orElse("-");
            case 7 -> {
                var dimensions = getImageDimensions();
                var pixelType = getPixelType();

                if (dimensions.isPresent() && pixelType.isPresent()) {
                    var quPathPixelType = ApisHandler.getPixelType(pixelType.get());

                    if (quPathPixelType.isPresent()) {
                        int width = dimensions.get()[0];
                        int height = dimensions.get()[1];
                        int bytesPerPixel = quPathPixelType.get().getBytesPerPixel();
                        int nChannels = dimensions.get()[2];
                        int zSlices = dimensions.get()[3];
                        int timePoints = dimensions.get()[4];

                        double size = width / 1024.0 * height / 1024.0 *
                                bytesPerPixel * nChannels *
                                zSlices * timePoints;
                        String unit = "MB";
                        if (size > 1000) {
                            size /= 1024;
                            unit = "GB";
                        }
                        yield String.format("%.1f %s", size, unit);
                    }
                }
                yield "-";
            }
            case 8 -> getImageDimensions().map(d -> String.valueOf(d[2])).orElse("-");
            case 9 -> getImageDimensions().map(d -> String.valueOf(d[3])).orElse("-");
            case 10 -> getImageDimensions().map(d -> String.valueOf(d[4])).orElse("-");
            case 11 -> getPhysicalSizeX().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 12 -> getPhysicalSizeY().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 13 -> getPhysicalSizeZ().map(x -> x.getValue() + " " + x.getSymbol().orElse("")).orElse("-");
            case 14 -> getPixelType().orElse("-");
            default -> "";
        };
    }

    @Override
    public int getNumberOfAttributes() {
        return ATTRIBUTES.length;
    }

    @Override
    public String toString() {
        return String.format("Image %s of ID %d", name, id);
    }

    /**
     * Indicates if an OMERO entity type refers to an image
     *
     * @param type  the OMERO entity type
     * @return whether this type refers to an image
     */
    public static boolean isImage(String type) {
        return "http://www.openmicroscopy.org/Schemas/OME/2016-06#Image".equalsIgnoreCase(type) || "Image".equalsIgnoreCase(type);
    }

    /**
     * Set the web client of this image. This is needed to determine if this image
     * can be opened.
     *
     * @param client the web client of this image
     */
    public void setWebClient(WebClient client) {
        isSupported = new SimpleBooleanProperty();
        unsupportedReasons = EnumSet.noneOf(UNSUPPORTED_REASON.class);

        setSupported(client);
        client.getSelectedPixelAPI().addListener(change -> setSupported(client));
    }

    /**
     * @return whether this image can be opened within QuPath. This property may be updated
     * from any thread
     * @throws IllegalStateException when the web client has not been set (see {@link #setWebClient(WebClient)})
     */
    public ReadOnlyBooleanProperty isSupported() {
        if (isSupported == null) {
            throw new IllegalStateException(
                    "The web client has not been set on this image. See the setWebClient(WebClient) function."
            );
        }
        return isSupported;
    }

    /**
     * @return the reasons why this image is unsupported (empty if this image is supported)
     * @throws IllegalStateException when the web client has not been set (see {@link #setWebClient(WebClient)})
     */
    public Set<UNSUPPORTED_REASON> getUnsupportedReasons() {
        if (unsupportedReasons == null) {
            throw new IllegalStateException(
                    "The web client has not been set on this image. See the setWebClient(WebClient) function."
            );
        }
        return unsupportedReasons;
    }

    /**
     * @return a text describing the pixel type of this image, or an empty Optional if not found
     */
    public Optional<String> getPixelType() {
        return pixels == null ? Optional.empty() : pixels.getPixelType();
    }

    private Optional<int[]> getImageDimensions() {
        if (pixels == null) {
            return Optional.empty();
        } else {
            return Optional.of(pixels.getImageDimensions());
        }
    }

    private Optional<PhysicalSize> getPhysicalSizeX() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeX();
    }

    private Optional<PhysicalSize> getPhysicalSizeY() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeY();
    }

    private Optional<PhysicalSize> getPhysicalSizeZ() {
        return pixels == null ? Optional.empty() : pixels.getPhysicalSizeZ();
    }

    private void setSupported(WebClient client) {
        isSupported.set(true);
        unsupportedReasons.clear();

        if (client.getSelectedPixelAPI() == null) {
            isSupported.set(false);
            unsupportedReasons.add(UNSUPPORTED_REASON.PIXEL_API_UNAVAILABLE);
        } else {
            if (!getPixelType()
                    .flatMap(ApisHandler::getPixelType)
                    .map(pixelType -> client.getSelectedPixelAPI().get().canReadImage(pixelType))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UNSUPPORTED_REASON.PIXEL_TYPE);
            }

            if (!getImageDimensions()
                    .map(imageDimensions -> client.getSelectedPixelAPI().get().canReadImage(imageDimensions[3]))
                    .orElse(false)
            ) {
                isSupported.set(false);
                unsupportedReasons.add(UNSUPPORTED_REASON.NUMBER_OF_CHANNELS);
            }
        }
    }
}
