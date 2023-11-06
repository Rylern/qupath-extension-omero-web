package qupath.ext.omero.core.apis;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.omero.core.apis.authenticators.Authenticator;
import qupath.ext.omero.core.entities.login.LoginResponse;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.shapes.Shape;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.serverinformation.OmeroAPI;
import qupath.ext.omero.core.entities.serverinformation.OmeroServerList;
import qupath.ext.omero.core.WebUtilities;
import qupath.ext.omero.core.RequestSender;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.ServerEntity;

import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * <p>The OMERO <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html">JSON API</a>.</p>
 * <p>
 *     This API is used to get basic information on the server, authenticate, get details on OMERO entities
 *     (e.g. images, datasets), and get ROIs of an image.
 * </p>
 */
class JsonApi {

    private static final Logger logger = LoggerFactory.getLogger(JsonApi.class);
    private static final String OWNERS_URL_KEY = "url:experimenters";
    private static final String GROUPS_URL_KEY = "url:experimentergroups";
    private static final String PROJECTS_URL_KEY = "url:projects";
    private static final String DATASETS_URL_KEY = "url:datasets";
    private static final String IMAGES_URL_KEY = "url:images";
    private static final String TOKEN_URL_KEY = "url:token";
    private static final String SERVERS_URL_KEY = "url:servers";
    private static final String LOGIN_URL_KEY = "url:login";
    private static final String API_URL = "%s/api/";
    private static final String PROJECTS_URL = "%s?childCount=true";
    private static final String DATASETS_URL = "%s%d/datasets/?childCount=true";
    private static final String IMAGES_URL = "%s%d/images/?childCount=true";
    private static final String ORPHANED_DATASETS_URL = "%s?childCount=true&orphaned=true";
    private static final String ROIS_URL = "%s/api/v0/m/rois/?image=%s";
    private final IntegerProperty numberOfEntitiesLoading = new SimpleIntegerProperty(0);
    private final BooleanProperty areOrphanedImagesLoading = new SimpleBooleanProperty(false);
    private final IntegerProperty numberOfOrphanedImagesLoaded = new SimpleIntegerProperty(0);
    private final URI host;
    private final ApisHandler apisHandler;
    private Map<String, String> urls;
    private int serverID;
    private int port;
    private String token;

    private JsonApi(ApisHandler apisHandler, URI host) {
        this.apisHandler = apisHandler;
        this.host = host;
    }

    @Override
    public String toString() {
        return String.format("JSON API of %s", host);
    }

    /**
     * <p>Attempt to create a JSON API client.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param apisHandler  the RequestsHandler using this API. It is used to have access to other APIs
     * @param host  the base server URI (e.g. <a href="https://idr.openmicroscopy.org">https://idr.openmicroscopy.org</a>)
     * @return a CompletableFuture with the JSON API client, an empty Optional if an error occurred
     */
    public static CompletableFuture<Optional<JsonApi>> create(ApisHandler apisHandler, URI host) {
        JsonApi jsonApi = new JsonApi(apisHandler, host);

        return jsonApi.initialize().thenApply(initialized -> {
            if (initialized) {
                return Optional.of(jsonApi);
            } else {
                return Optional.empty();
            }
        });
    }

    /**
     * @return the <a href="https://docs.openmicroscopy.org/omero/5.6.0/developers/json-api.html#get-csrf-token">CSRF token</a> used by this session
     */
    public String getToken() {
        return token;
    }

    /**
     * @return the server port of this session
     */
    public int getPort() {
        return port;
    }

    /**
     * @return the number of OMERO entities (e.g. datasets, images) currently being loaded by the API.
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfEntitiesLoading() {
        return numberOfEntitiesLoading;
    }

    /**
     * <p>Attempt to authenticate to the current server.</p>
     * <p>This function is asynchronous.</p>
     *
     * <p>The arguments must have one of the following format:</p>
     * <ul>
     *     <li>{@code --username [username] --password [password]}</li>
     *     <li>{@code -u [username] -p [password]}</li>
     * </ul>
     * <p>
     *     If the arguments are not enough to authenticate, the user will
     *     automatically be asked for credentials.
     * </p>
     *
     * @return a CompletableFuture with the authentication status
     */
    public CompletableFuture<LoginResponse> login(String... args) {
        var uri = WebUtilities.createURI(urls.get(LOGIN_URL_KEY));

        PasswordAuthentication authentication = getPasswordAuthenticationFromArgs(args).orElseGet(() -> Authenticator.getPasswordAuthentication(host.toString()));

        if (uri.isEmpty() || authentication == null) {
            return CompletableFuture.completedFuture(LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.CANCELED));
        } else {
            char[] password = Arrays.copyOf(authentication.getPassword(), authentication.getPassword().length);
            char[] encodedPassword = ApiUtilities.urlEncode(authentication.getPassword());

            byte[] body = ApiUtilities.concatAndConvertToBytes(
                    String.join("&", "server=" + serverID, "username=" + authentication.getUserName(), "password=").toCharArray(),
                    encodedPassword
            );

            return RequestSender.post(
                    uri.get(),
                    body,
                    uri.get().toString(),
                    token
            ).thenApply(response -> {
                Arrays.fill(body, (byte) 0);

                if (response.isPresent()) {
                    return LoginResponse.createSuccessLoginResponse(response.get(), password);
                } else {
                    Arrays.fill(password, (char) 0);
                    return LoginResponse.createNonSuccessfulLoginResponse(LoginResponse.Status.FAILED);
                }
            });
        }
    }

    /**
     * <p>
     *     Attempt to retrieve all groups of the server.
     *     This doesn't include the default group.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the list containing all groups of this server,
     * or an empty list if the request failed
     */
    public CompletableFuture<List<Group>> getGroups() {
        var uri = WebUtilities.createURI(urls.get(GROUPS_URL_KEY));

        if (uri.isPresent()) {
            return RequestSender.getPaginated(uri.get()).thenApplyAsync(jsonElements -> {
                List<Group> groups = jsonElements.stream()
                        .map(jsonElement -> new Gson().fromJson(jsonElement, Group.class))
                        .toList();

                for (Group group: groups) {
                    var experimenterLink = WebUtilities.createURI(group.getExperimentersLink());
                    if (experimenterLink.isPresent()) {
                        try {
                            group.setOwners(RequestSender.getPaginated(experimenterLink.get()).get().stream()
                                    .map(jsonElement -> new Gson().fromJson(jsonElement, Owner.class))
                                    .toList());
                        } catch (InterruptedException | ExecutionException e) {
                            logger.warn("Couldn't retrieve owners of " + group, e);
                        }
                    }
                }

                return groups;
            });
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * <p>
     *     Attempt to retrieve all owners of the server.
     *     This doesn't include the default owner.
     * </p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the list containing all owners of this server,
     * or an empty list if the request failed
     */
    public CompletableFuture<List<Owner>> getOwners() {
        var uri = WebUtilities.createURI(urls.get(OWNERS_URL_KEY));

        if (uri.isPresent()) {
            return RequestSender.getPaginated(uri.get()).thenApply(jsonElements ->
                    jsonElements.stream().map(jsonElement -> new Gson().fromJson(jsonElement, Owner.class)).toList()
            );
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * <p>Attempt to retrieve all projects of the server.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the list containing all projects of this server
     */
    public CompletableFuture<List<Project>> getProjects() {
        return getChildren(String.format(PROJECTS_URL, urls.get(PROJECTS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Project) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all orphaned datasets of the server.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture with the list containing all orphaned datasets of this server
     */
    public CompletableFuture<List<Dataset>> getOrphanedDatasets() {
        return getChildren(String.format(ORPHANED_DATASETS_URL, urls.get(DATASETS_URL_KEY))).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all datasets of a project.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param projectID  the project ID whose datasets should be retrieved
     * @return a CompletableFuture with the list containing all datasets of the project
     */
    public CompletableFuture<List<Dataset>> getDatasets(long projectID) {
        return getChildren(String.format(DATASETS_URL, urls.get(PROJECTS_URL_KEY), projectID)).thenApply(
                children -> children.stream().map(child -> (Dataset) child).toList()
        );
    }

    /**
     * <p>Attempt to retrieve all images of a dataset.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param datasetID  the dataset ID whose images should be retrieved
     * @return a CompletableFuture with the list containing all images of the dataset
     */
    public CompletableFuture<List<Image>> getImages(long datasetID) {
        return getChildren(String.format(IMAGES_URL, urls.get(DATASETS_URL_KEY), datasetID)).thenApply(
                children -> children.stream().map(child -> (Image) child).toList()
        );
    }

    /**
     * <p>Attempt to create an Image entity from an image ID.</p>
     *
     * @param imageID  the ID of the image
     * @return a CompletableFuture with the image, or an empty Optional if it couldn't be retrieved
     */
    public CompletableFuture<Optional<Image>> getImage(long imageID) {
        var uri = WebUtilities.createURI(urls.get(IMAGES_URL_KEY) + imageID);

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), JsonElement.class).thenApply(jsonElement -> {
                if (jsonElement.isPresent()) {
                    var serverEntity = ServerEntity.createFromJsonElement(jsonElement.get().getAsJsonObject().get("data"), apisHandler);
                    if (serverEntity.isPresent() && serverEntity.get() instanceof Image image) {
                        return Optional.of(image);
                    }
                }
                return Optional.empty();
            });
        } else {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /**
     * <p>Attempt to get the number of orphaned images of this server.</p>
     *
     * @return a CompletableFuture with the number of orphaned images, or 0 if it couldn't be retrieved
     */
    public CompletableFuture<Integer> getNumberOfOrphanedImages() {
        return apisHandler.getOrphanedImagesIds().thenApply(ids -> ids.stream()
                .map(id -> WebUtilities.createURI(urls.get(IMAGES_URL_KEY) + id))
                .flatMap(Optional::stream)
                .toList()
        ).thenApply(List::size);
    }

    /**
     * <p>
     *     Populate all orphaned images of this server to the list specified in parameter.
     *     This function populates and doesn't return a list because the number of images can
     *     be large, so this operation can take tens of seconds.
     * </p>
     * <p>The list can be updated from any thread.</p>
     *
     * @param children  the list which should be populated by the orphaned images. It should
     *                  be possible to add elements to this list
     */
    public void populateOrphanedImagesIntoList(List<Image> children) {
        setOrphanedImagesLoading(true);
        resetNumberOfOrphanedImagesLoaded();

        getOrphanedImagesURIs().thenAcceptAsync(uris -> {
            // The number of parallel requests is limited to 16
            // to avoid too many concurrent streams
            List<List<URI>> batches = Lists.partition(uris, 16);
            for (List<URI> batch: batches) {
                List<Image> serverEntities = batch.stream()
                        .map(this::requestImageInfo)
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .map(jsonObject -> ServerEntity.createFromJsonElement(jsonObject, apisHandler))
                        .flatMap(Optional::stream)
                        .map(serverEntity -> (Image) serverEntity)
                        .toList();

                children.addAll(serverEntities);

                addToNumberOfOrphanedImagesLoaded(batch.size());
            }

            setOrphanedImagesLoading(false);
        });
    }

    /**
     * @return whether orphaned images are currently being loaded.
     * This property may be updated from any thread
     */
    public ReadOnlyBooleanProperty areOrphanedImagesLoading() {
        return areOrphanedImagesLoading;
    }

    /**
     * @return the number of orphaned images which have been loaded
     * This property may be updated from any thread
     */
    public ReadOnlyIntegerProperty getNumberOfOrphanedImagesLoaded() {
        return numberOfOrphanedImagesLoaded;
    }

    /**
     * <p>Indicates if the server can be browsed without being authenticated.</p>
     * <p>This works only if the client isn't already authenticated to the server.</p>
     * <p>This function is asynchronous.</p>
     *
     * @return a CompletableFuture indicating if authentication can be skipped
     */
    public CompletableFuture<Boolean> canSkipAuthentication() {
        var uri = WebUtilities.createURI(urls.get(PROJECTS_URL_KEY));

        if (uri.isPresent()) {
            return RequestSender.isLinkReachable(uri.get());
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * <p>Attempt to retrieve ROIs of an image.</p>
     * <p>This function is asynchronous.</p>
     *
     * @param id  the OMERO image ID
     * @return a CompletableFuture with the list of ROIs. If an error occurs, the list is empty
     */
    public CompletableFuture<List<Shape>> getROIs(long id) {
        var uri = WebUtilities.createURI(String.format(ROIS_URL, host, id));

        if (uri.isPresent()) {
            var gson = new GsonBuilder().registerTypeAdapter(Shape.class, new Shape.GsonShapeDeserializer()).setLenient().create();

            return RequestSender.getPaginated(uri.get()).thenApply(jsonElements -> jsonElements.stream()
                    .map(datum -> {
                        int roiID = datum.getAsJsonObject().get("@id").getAsInt();

                        return datum.getAsJsonObject().getAsJsonArray("shapes").asList().stream()
                                .map(jsonElement -> {
                                    try {
                                        return gson.fromJson(jsonElement, Shape.class);
                                    } catch (JsonSyntaxException e) {
                                        logger.error("Error parsing shape", e);
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .peek(shape -> shape.setOldId(roiID))
                                .toList();
                    })
                    .flatMap(List::stream)
                    .toList());
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private CompletableFuture<Map<String, String>> getURLs(String url) {
        var uri = WebUtilities.createURI(url);

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), new TypeToken<Map<String, String>>() {}).thenApply(response -> response.orElse(Map.of()));
        } else {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private CompletableFuture<Boolean> initialize() {
        var uri = WebUtilities.createURI(String.format(API_URL, host));

        if (uri.isPresent()) {
            return RequestSender.getAndConvert(uri.get(), OmeroAPI.class)
                    .thenCompose(omeroAPI -> {
                        if (omeroAPI.isPresent() && omeroAPI.get().getLatestVersionURL().isPresent()) {
                            return getURLs(omeroAPI.get().getLatestVersionURL().get());
                        } else {
                            return CompletableFuture.completedFuture(Map.of());
                        }
                    })
                    .thenApplyAsync(urls -> {
                        if (urls.isEmpty()) {
                            logger.error("Could not find API URL in " + uri.get());
                            return false;
                        } else {
                            if (setServerIDAndPort(urls).join() && setToken(urls).join()) {
                                this.urls = urls;

                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
        } else {
            return CompletableFuture.completedFuture(false);
        }
    }

    private synchronized void setOrphanedImagesLoading(boolean orphanedImagesLoading) {
        areOrphanedImagesLoading.set(orphanedImagesLoading);
    }

    private CompletableFuture<List<URI>> getOrphanedImagesURIs() {
        return apisHandler.getOrphanedImagesIds().thenApply(ids -> ids.stream()
                .map(id -> WebUtilities.createURI(urls.get(IMAGES_URL_KEY) + id))
                .flatMap(Optional::stream)
                .toList()
        );
    }

    private synchronized void resetNumberOfOrphanedImagesLoaded() {
        numberOfOrphanedImagesLoaded.set(0);
    }

    private synchronized void addToNumberOfOrphanedImagesLoaded(int addition) {
        numberOfOrphanedImagesLoaded.set(numberOfOrphanedImagesLoaded.get() + addition);
    }

    private CompletableFuture<Boolean> setServerIDAndPort(Map<String, String> urls) {
        String url = SERVERS_URL_KEY;

        if (urls.containsKey(url)) {
            var uri = WebUtilities.createURI(urls.get(url));

            if (uri.isPresent()) {
                return RequestSender.getAndConvert(
                        uri.get(),
                        OmeroServerList.class
                ).thenApply(serverList -> {
                    if (serverList.isPresent() && serverList.get().getServerId().isPresent() && serverList.get().getServerPort().isPresent()) {
                        serverID = serverList.get().getServerId().getAsInt();
                        port = serverList.get().getServerPort().getAsInt();
                        return true;
                    } else {
                        logger.error("Couldn't get id. The server response doesn't contain the required information.");
                        return false;
                    }
                });
            } else {
                return CompletableFuture.completedFuture(false);
            }
        } else {
            logger.error("Couldn't find the URL corresponding to " + url);
            return CompletableFuture.completedFuture(false);
        }
    }

    private CompletableFuture<Boolean> setToken(Map<String, String> urls) {
        String url = TOKEN_URL_KEY;

        if (urls.containsKey(url)) {
            var uri = WebUtilities.createURI(urls.get(url));

            if (uri.isPresent()) {
                return RequestSender.getAndConvert(
                        uri.get(),
                        new TypeToken<Map<String, String>>() {}
                ).thenApply(response -> {
                    boolean canGetToken = response.isPresent() && response.get().containsKey("data");

                    if (canGetToken) {
                        token = response.get().get("data");
                    } else {
                        logger.error("Couldn't get token. The server response doesn't contain the required information.");
                    }

                    return canGetToken;
                });
            } else {
                return CompletableFuture.completedFuture(false);
            }
        } else {
            logger.error("Couldn't find the URL corresponding to " + url);
            return CompletableFuture.completedFuture(false);
        }
    }

    private static Optional<PasswordAuthentication> getPasswordAuthenticationFromArgs(String... args) {
        String username = null;
        char[] password = null;
        int i = 0;
        while (i < args.length-1) {
            String parameter = args[i++];
            if ("--username".equals(parameter) || "-u".equals(parameter)) {
                username = args[i++];
            }
            else if ("--password".equals(parameter) || "-p".equals(parameter)) {
                password = args[i++].toCharArray();
            }
        }

        if (username != null && password != null) {
            return Optional.of(new PasswordAuthentication(username, password));
        } else {
            return Optional.empty();
        }
    }

    private CompletableFuture<List<ServerEntity>> getChildren(String url) {
        var uri = WebUtilities.createURI(url);

        if (uri.isPresent()) {
            changeNumberOfEntitiesLoading(true);

            return RequestSender.getPaginated(uri.get()).thenApply(jsonElements -> {
                changeNumberOfEntitiesLoading(false);

                return ServerEntity.createFromJsonElements(jsonElements, apisHandler).toList();
            });
        } else {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private CompletableFuture<Optional<JsonObject>> requestImageInfo(URI uri) {
        return RequestSender.getAndConvert(uri, JsonObject.class).thenApply(response -> {
            if (response.isPresent()) {
                try  {
                    return Optional.ofNullable(response.get().getAsJsonObject("data"));
                } catch (Exception e) {
                    logger.error("Cannot read 'data' member in " + response, e);
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        });
    }

    private synchronized void changeNumberOfEntitiesLoading(boolean increment) {
        int quantityToAdd = increment ? 1 : -1;
        numberOfEntitiesLoading.set(numberOfEntitiesLoading.get() + quantityToAdd);
    }
}
