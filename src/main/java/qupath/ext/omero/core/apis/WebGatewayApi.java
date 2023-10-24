package qupath.ext.omero.core.apis;

import com.google.gson.JsonObject;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.TileRequest;
import qupath.ext.omero.core.entities.imagemetadata.ImageMetadataResponse;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.RequestSender;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * <p>API to communicate with a OMERO.gateway server.</p>
 * <p>
 *     This API is used to retrieve several icons, image thumbnails and
 *     provides access to the pixels of JPEG-compressed RGB encoded images.
 * </p>
 */
class WebGatewayApi {

    private static final String ICON_URL = "%s/static/webgateway/img/%s";
    private static final String PROJECT_ICON_NAME = "folder16.png";
    private static final String DATASET_ICON_NAME = "folder_image16.png";
    private static final String ORPHANED_FOLDER_ICON_NAME = "folder_yellow16.png";
    private static final String THUMBNAIL_URL = "%s/webgateway/render_thumbnail/%d/%d";
    private static final String IMAGE_DATA_URL = "%s/webgateway/imgData/%d";
    private static final String SINGLE_RESOLUTION_TILE_URL = "%s/webgateway/render_image_region/%d/%d/%d" +
            "/?region=%d,%d,%d,%d" +
            "&%s" +
            "&%s" +
            "&m=c&p=normal&q=%f";
    private static final String MULTI_RESOLUTION_TILE_URL = "%s/webgateway/render_image_region/%d/%d/%d" +
            "/?tile=%d,%d,%d,%d,%d" +
            "&%s" +
            "&%s" +
            "&m=c&p=normal&q=%f";
    private static final String TILE_FIRST_PARAMETER = URLEncoder.encode("c=1|0:255$FF0000,2|0:255$00FF00,3|0:255$0000FF", StandardCharsets.UTF_8);
    private static final String TILE_SECOND_PARAMETER =
            URLEncoder.encode("maps=[{\"inverted\":{\"enabled\":false}},{\"inverted\":{\"enabled\":false}},{\"inverted\":{\"enabled\":false}}]", StandardCharsets.UTF_8);
    private final IntegerProperty numberOfThumbnailsLoading = new SimpleIntegerProperty(0);
    private final URI host;

    /**
     * Creates a web gateway client.
     *
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     */
    public WebGatewayApi(URI host) {
        this.host = host;
    }

    @Override
    public String toString() {
        return String.format("WebGateway API of %s", host);
    }

    /**
     * @return the number of thumbnails currently being loaded.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfThumbnailsLoading() {
        return numberOfThumbnailsLoading;
    }

    /**
     * <p>Attempt to retrieve the OMERO project icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the project icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getProjectIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, PROJECT_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the OMERO dataset icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the dataset icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getDatasetIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, DATASET_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the OMERO orphaned folder icon.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the orphaned folder icon, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getOrphanedFolderIcon() {
        return ApiUtilities.getImage(String.format(ICON_URL, host, ORPHANED_FOLDER_ICON_NAME));
    }

    /**
     * <p>Attempt to retrieve the thumbnail of an image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @param size  the width and height the thumbnail should have
     * @return a CompletableFuture with the thumbnail, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> getThumbnail(long id, int size) {
        changeNumberOfThumbnailsLoading(true);

        return ApiUtilities.getImage(String.format(THUMBNAIL_URL, host, id, size)).thenApply(thumbnail -> {
            changeNumberOfThumbnailsLoading(false);
            return thumbnail;
        });
    }

    /**
     * <p>Attempt to retrieve the metadata of an image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @return a CompletableFuture with the metadata, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<ImageMetadataResponse>> getImageMetadata(long id) {
        var uri = WebUtilities.createURI(String.format(IMAGE_DATA_URL, host, id));

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), JsonObject.class).thenApply(jsonObject -> {
                if (jsonObject.isPresent()) {
                    return ImageMetadataResponse.createFromJson(jsonObject.get());
                } else {
                    return Optional.empty();
                }
            });
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * <p>Attempt to read a tile (portion of image) from a single resolution image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @param tileRequest  the tile request (usually coming from the {@link qupath.lib.images.servers.AbstractTileableImageServer AbstractTileableImageServer})
     * @param preferredTileWidth  the preferred tile width in pixels
     * @param preferredTileHeight  the preferred tile height in pixels
     * @param quality  the JPEG quality, from 0 to 1
     * @param allowSmoothInterpolation  whether to use smooth interpolation when resizing
     * @return a CompletableFuture with the tile, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> readSingleResolutionTile(Long id, TileRequest tileRequest, int preferredTileWidth, int preferredTileHeight, double quality, boolean allowSmoothInterpolation) {
        return ApiUtilities.getImage(String.format(SINGLE_RESOLUTION_TILE_URL,
                        host, id, tileRequest.getZ(), tileRequest.getT(),
                        tileRequest.getTileX(), tileRequest.getTileY(), preferredTileWidth, preferredTileHeight,
                        TILE_FIRST_PARAMETER,
                        TILE_SECOND_PARAMETER,
                        quality
                )
        )
                .thenApply(bufferedImage ->
                        bufferedImage.map(image -> BufferedImageTools.resize(image, tileRequest.getTileWidth(), tileRequest.getTileHeight(), allowSmoothInterpolation))
                );
    }

    /**
     * <p>Attempt to read a tile (portion of image) from a multi resolution image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @param tileRequest  the tile request (usually coming from the {@link qupath.lib.images.servers.AbstractTileableImageServer AbstractTileableImageServer})
     * @param preferredTileWidth  the preferred tile width in pixels
     * @param preferredTileHeight  the preferred tile height in pixels
     * @param quality  the JPEG quality, from 0 to 1
     * @return a CompletableFuture with the tile, or an empty Optional if an error occurred
     */
    public CompletableFuture<Optional<BufferedImage>> readMultiResolutionTile(Long id, TileRequest tileRequest, int preferredTileWidth, int preferredTileHeight, double quality) {
        return ApiUtilities.getImage(String.format(MULTI_RESOLUTION_TILE_URL,
                host, id, tileRequest.getZ(), tileRequest.getT(),
                tileRequest.getLevel(), tileRequest.getTileX() / preferredTileWidth, tileRequest.getTileY() / preferredTileHeight,
                preferredTileWidth, preferredTileHeight,
                TILE_FIRST_PARAMETER,
                TILE_SECOND_PARAMETER,
                quality
        ));
    }

    private synchronized void changeNumberOfThumbnailsLoading(boolean increment) {
        int quantityToAdd = increment ? 1 : -1;
        numberOfThumbnailsLoading.set(numberOfThumbnailsLoading.get() + quantityToAdd);
    }
}
