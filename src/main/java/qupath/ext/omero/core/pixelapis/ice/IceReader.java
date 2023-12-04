package qupath.ext.omero.core.pixelapis.ice;

import omero.ServerError;
import omero.api.RawPixelsStorePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.model.ExperimenterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.apis.ApisHandler;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.bioformats.OMEPixelParser;
import qupath.ext.omero.core.pixelapis.PixelAPIReader;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Read pixel values using the <a href="https://omero.readthedocs.io/en/v5.6.7/developers/Java.html">OMERO gateway</a>.
 */
class IceReader implements PixelAPIReader {

    private static final Logger logger = LoggerFactory.getLogger(IceReader.class);
    private final Gateway gateway = new Gateway(new IceLogger());
    private final RawPixelsStorePrx reader;
    private final int numberOfResolutionLevels;
    private final int nChannels;
    private final int effectiveNChannels;
    private final PixelType pixelType;
    private final ColorModel colorModel;
    private SecurityContext context;

    /**
     * Creates a new Ice reader.
     *
     * @param client  the WebClient owning the image to open
     * @param imageID  the ID of the image to open
     * @param channels  the channels of the image to open
     * @throws IOException  when the reader creation fails
     */
    public IceReader(WebClient client, long imageID, List<ImageChannel> channels) throws IOException {
        try {
            ExperimenterData user = connect(client);

            context = new SecurityContext(user.getGroupId());

            var imageData = getImage(imageID);
            if (imageData.isPresent()) {
                PixelsData pixelsData = imageData.get().getDefaultPixels();

                reader = gateway.getPixelsStore(context);
                reader.setPixelsId(pixelsData.getId(), false);
                numberOfResolutionLevels = reader.getResolutionLevels();
                nChannels = channels.size();
                effectiveNChannels = pixelsData.getSizeC();
                pixelType = switch (pixelsData.getPixelType()) {
                    case PixelsData.INT8_TYPE -> PixelType.INT8;
                    case PixelsData.UINT8_TYPE -> PixelType.UINT8;
                    case PixelsData.INT16_TYPE -> PixelType.INT16;
                    case PixelsData.UINT16_TYPE -> PixelType.UINT16;
                    case PixelsData.UINT32_TYPE -> PixelType.UINT32;
                    case PixelsData.INT32_TYPE -> PixelType.INT32;
                    case PixelsData.FLOAT_TYPE -> PixelType.FLOAT32;
                    case PixelsData.DOUBLE_TYPE -> PixelType.FLOAT64;
                    default -> throw new IllegalArgumentException("Unsupported pixel type " + pixelsData.getPixelType());
                };
                colorModel = ColorModelFactory.createColorModel(pixelType, channels);
            } else {
                throw new IOException("Couldn't find requested image of ID " + imageID);
            }
        } catch (DSOutOfServiceException | ExecutionException | ServerError e) {
            throw new IOException(e);
        }
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
        byte[][] bytes = new byte[effectiveNChannels][];

        synchronized (reader) {
            try {
                reader.setResolutionLevel(numberOfResolutionLevels - 1 - tileRequest.getLevel());
            } catch (ServerError e) {
                throw new IOException(e);
            }

            for (int channel = 0; channel < effectiveNChannels; channel++) {
                try {
                    bytes[channel] = reader.getTile(
                            tileRequest.getZ(),
                            channel,
                            tileRequest.getT(),
                            tileRequest.getTileX(),
                            tileRequest.getTileY(),
                            tileRequest.getTileWidth(),
                            tileRequest.getTileHeight()
                    );
                } catch (ServerError e) {
                    throw new IOException(e);
                }
            }
        }

        OMEPixelParser omePixelParser = new OMEPixelParser.Builder()
                .isInterleaved(false)
                .pixelType(pixelType)
                .byteOrder(ByteOrder.BIG_ENDIAN)
                .normalizeFloats(false)
                .effectiveNChannels(effectiveNChannels)
                .build();

        return omePixelParser.parse(bytes, tileRequest.getTileWidth(), tileRequest.getTileHeight(), nChannels, colorModel);
    }

    @Override
    public String getName() {
        return IceAPI.NAME;
    }

    @Override
    public void close() {
        gateway.disconnect();
    }

    @Override
    public String toString() {
        return String.format("Ice reader for %s", context.getServerInformation());
    }

    /**
     * Attempt to create a connection with the server. The OMERO web host will be
     * used, and if not successful, the OMERO server host will be used (see
     * {@link ApisHandler#getServerURI()}).
     *
     * @param client  the connection to use
     * @return a valid connection
     * @throws DSOutOfServiceException when a connection cannot be established
     */
    private ExperimenterData connect(WebClient client) throws DSOutOfServiceException {
        String firstURI = client.getApisHandler().getWebServerURI().getHost();
        String secondURI = client.getApisHandler().getServerURI();

        try {
            return gateway.connect(new LoginCredentials(
                    client.getSessionUuid().orElse(""),
                    client.getSessionUuid().orElse(""),
                    firstURI,
                    client.getApisHandler().getServerPort()
            ));
        } catch (DSOutOfServiceException e) {
            logger.warn(String.format(
                    "Can't connect to %s:%d. Trying %s:%d...",
                    firstURI,
                    client.getApisHandler().getServerPort(),
                    secondURI,
                    client.getApisHandler().getServerPort()
            ), e);

            return gateway.connect(new LoginCredentials(
                    client.getSessionUuid().orElse(""),
                    client.getSessionUuid().orElse(""),
                    secondURI,
                    client.getApisHandler().getServerPort()
            ));
        }
    }

    private Optional<ImageData> getImage(long imageID) throws ExecutionException, DSOutOfServiceException, ServerError {
        BrowseFacility browser = gateway.getFacility(BrowseFacility.class);
        try {
            return Optional.of(browser.getImage(context, imageID));
        } catch (Exception ignored) {}

        List<ExperimenterGroup> groups = gateway.getAdminService(context).containedGroups(gateway.getLoggedInUser().asExperimenter().getId().getValue());
        for (ExperimenterGroup group: groups) {
            context = new SecurityContext(group.getId().getValue());

            try {
                return Optional.of(browser.getImage(context, imageID));
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }
}
