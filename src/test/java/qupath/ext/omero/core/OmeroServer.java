package qupath.ext.omero.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import qupath.ext.omero.core.entities.annotations.AnnotationGroup;
import qupath.ext.omero.core.entities.permissions.Group;
import qupath.ext.omero.core.entities.permissions.Owner;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Dataset;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.Project;
import qupath.ext.omero.core.entities.repositoryentities.serverentities.image.Image;
import qupath.ext.omero.core.entities.search.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *     An abstract class that gives access to an OMERO server hosted
 *     on a local Docker container. Each subclass of this class will
 *     have access to the same OMERO server.
 * </p>
 * <p>
 *     All useful information of the OMERO server are returned by functions
 *     of this class.
 * </p>
 * <p>
 *     If Docker can't be found on the host machine, all tests are skipped.
 * </p>
 * <p>
 *     If a Docker container containing a working OMERO server is already
 *     running on the host machine, set the {@link #IS_LOCAL_OMERO_SERVER_RUNNING}
 *     variable to {@code true}. This will prevent this class to create a new container,
 *     gaining some time when running the tests.
 * </p>
 */
public abstract class OmeroServer {

    private static final boolean IS_LOCAL_OMERO_SERVER_RUNNING = false;
    private static final int CLIENT_CREATION_ATTEMPTS = 3;
    private static final String OMERO_PASSWORD = "password";
    private static final int OMERO_SERVER_PORT = 4064;
    private static final GenericContainer<?> postgres;
    private static final GenericContainer<?> omeroServer;
    private static final GenericContainer<?> omeroWeb;
    private static final boolean dockerAvailable;
    private static final String analysisFileId;

    static {
        dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        if (!dockerAvailable || IS_LOCAL_OMERO_SERVER_RUNNING) {
            postgres = null;
            omeroServer = null;
            omeroWeb = null;
            analysisFileId = "85";
        } else {
            postgres = new GenericContainer<>(DockerImageName.parse("postgres"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("postgres")
                    .withEnv("POSTGRES_PASSWORD", "postgres");
            postgres.start();

            omeroServer = new GenericContainer<>(DockerImageName.parse("openmicroscopy/omero-server"))
                    .withNetwork(Network.SHARED)
                    .withNetworkAliases("omero-server")
                    .withEnv("CONFIG_omero_db_host", "postgres")
                    .withEnv("CONFIG_omero_db_user", "postgres")
                    .withEnv("CONFIG_omero_db_pass", "postgres")
                    .withEnv("CONFIG_omero_db_name", "postgres")
                    .withEnv("ROOTPASS", OMERO_PASSWORD)
                    .withExposedPorts(OMERO_SERVER_PORT)
                    .withFileSystemBind(
                            Objects.requireNonNull(OmeroServer.class.getResource("analysis.csv")).getPath(),
                            "/analysis.csv"
                    )
                    .withFileSystemBind(
                            Objects.requireNonNull(OmeroServer.class.getResource("Dot_Blot.tif")).getPath(),
                            "/Dot_Blot.tif"
                    )
                    .withFileSystemBind(
                            Objects.requireNonNull(OmeroServer.class.getResource("mitosis.tif")).getPath(),
                            "/mitosis.tif"
                    )
                    .withFileSystemBind(
                            Objects.requireNonNull(OmeroServer.class.getResource("setupOmeroServer.sh")).getPath(),
                            "/setupOmeroServer.sh"
                    )
                    .dependsOn(postgres);
            omeroServer.start();

            omeroWeb = new GenericContainer<>(DockerImageName.parse("openmicroscopy/omero-web-standalone"))
                    .withNetwork(Network.SHARED)
                    .withEnv("OMEROHOST", "omero-server")
                    .withExposedPorts(4080)
                    .dependsOn(omeroServer);
            omeroWeb.start();

            try {
                TimeUnit.SECONDS.sleep(10);     // Give time for the server to start

                omeroServer.execInContainer("chmod", "+x", "/setupOmeroServer.sh");

                Container.ExecResult result = omeroServer.execInContainer("/setupOmeroServer.sh");
                String[] logs = result.getStdout().split("\n");
                analysisFileId = logs[logs.length-1].split(":")[1];
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @BeforeAll
    static void shouldRunTest() {
        Assumptions.assumeTrue(dockerAvailable, "Aborting test: no docker environment detected");
    }

    protected static String getServerURL() {
        return omeroWeb == null ? "http://localhost:4080" : "http://" + omeroWeb.getHost() + ":" + omeroWeb.getMappedPort(4080);
    }

    protected static WebClient createValidClient() throws ExecutionException, InterruptedException {
        WebClient webClient;
        int attempt = 0;

        do {
            webClient = WebClients.createClient(
                    getServerURL(),
                    "-u",
                    getUsername(),
                    "-p",
                    getPassword()
            ).get();
        } while (!webClient.getStatus().equals(WebClient.Status.SUCCESS) && ++attempt < CLIENT_CREATION_ATTEMPTS);

        if (webClient.getStatus().equals(WebClient.Status.SUCCESS)) {
            return webClient;
        } else {
            throw new IllegalStateException("Client creation failed");
        }
    }

    protected static int getPort() {
        return OMERO_SERVER_PORT;
    }

    protected static String getUsername() {
        return "root";
    }

    protected static String getPassword() {
        return OMERO_PASSWORD;
    }

    protected static Project getProject() {
        return new Project(1);
    }

    protected static String getProjectURI() {
        return getServerURL() + "/webclient/?show=project-" + getProject().getId();
    }

    protected static String getProjectAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> "project";
            case 1 -> String.valueOf(getProject().getId());
            case 2 -> "-";
            case 3 -> getCurrentOwner().getFullName();
            case 4 -> getCurrentGroup().getName();
            case 5 -> "1";
            default -> "";
        };
    }

    protected static int getProjectNumberOfAttributes() {
        return 6;
    }

    protected static Dataset getOrphanedDataset() {
        return new Dataset(2);
    }

    protected static Dataset getDataset() {
        return new Dataset(1);
    }

    protected static String getDatasetURI() {
        return getServerURL() + "/webclient/?show=dataset-" + getDataset().getId();
    }

    protected static AnnotationGroup getDatasetAnnotationGroup() {
        return new AnnotationGroup(JsonParser.parseString(String.format("""
                {
                    "annotations": [
                        {
                            "owner": {
                                "id": 0
                            },
                            "link": {
                                "owner": {
                                    "id": 0
                                }
                            },
                            "class": "CommentAnnotationI",
                            "textValue": "comment"
                        },
                        {
                            "owner": {
                                "id": 0
                            },
                            "link": {
                                "owner": {
                                    "id": 0
                                }
                            },
                            "class": "FileAnnotationI",
                            "file": {
                                "id": %s,
                                "name": "analysis.csv",
                                "size": 15,
                                "path": "//",
                                "mimetype": "text/csv"
                            }
                        }
                   ],
                   "experimenters": [
                        {
                            "id": 0,
                            "firstName": "root",
                            "lastName": "root"
                        }
                   ]
                }
                """, analysisFileId)).getAsJsonObject());
    }

    protected static String getDatasetAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> "dataset";
            case 1 -> String.valueOf(getDataset().getId());
            case 2 -> "-";
            case 3 -> getCurrentOwner().getFullName();
            case 4 -> getCurrentGroup().getName();
            case 5 -> "1";
            default -> "";
        };
    }

    protected static int getDatasetNumberOfAttributes() {
        return 6;
    }

    protected static List<SearchResult> getSearchResultsOnDataset() {
        return List.of(
                new SearchResult(
                        "dataset",
                        1,
                        "dataset",
                        getCurrentGroup().getName(),
                        "/webclient/?show=dataset-1",
                        null,
                        null
                ),
                new SearchResult(
                        "dataset",
                        2,
                        "orphaned_dataset",
                        getCurrentGroup().getName(),
                        "/webclient/?show=dataset-2",
                        null,
                        null
                )
        );
    }

    protected static Image getImage() {
        return new Image(1);
    }

    protected static URI getImageURI() {
        return URI.create(getServerURL() + "/webclient/?show=image-" + getImage().getId());
    }

    protected static String getImageAttributeValue(int informationIndex) {
        return switch (informationIndex) {
            case 0 -> "mitosis.tif";
            case 1 -> String.valueOf(getImage().getId());
            case 2 -> getCurrentOwner().getFullName();
            case 3 -> getCurrentGroup().getName();
            case 4, 13 -> "-";
            case 5 -> "171 px";
            case 6 -> "196 px";
            case 7 -> "32.6 MB";
            case 8 -> "5";
            case 9 -> "2";
            case 10 -> "51";
            case 11, 12 -> "0.08850000022125 µm";
            case 14 -> "uint16";
            default -> "";
        };
    }

    protected static int getImageNumberOfAttributes() {
        return 15;
    }

    protected static Image getOrphanedImage() {
        return new Image(2);
    }

    protected static List<Group> getGroups() {
        return List.of(
                new Group(0, "system"),
                new Group(1, "user"),
                new Group(2, "guest")
        );
    }

    protected static List<Owner> getOwners() {
        return List.of(
                new Owner(0, "root", "", "root", "", "", "root"),
                new Owner(1, "Guest", "", "Account", "", "", "guest")
        );
    }

    protected static Group getCurrentGroup() {
        return getGroups().get(0);
    }

    protected static Owner getCurrentOwner() {
        return getOwners().get(0);
    }
}