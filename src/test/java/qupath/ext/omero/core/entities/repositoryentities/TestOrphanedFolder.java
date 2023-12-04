package qupath.ext.omero.core.entities.repositoryentities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import qupath.ext.omero.TestUtilities;
import qupath.ext.omero.OmeroServer;
import qupath.ext.omero.core.WebClient;
import qupath.ext.omero.core.WebClients;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TestOrphanedFolder extends OmeroServer {

    private static WebClient client;
    private static OrphanedFolder orphanedFolder;
    @BeforeAll
    static void createClient() throws ExecutionException, InterruptedException {
        client = OmeroServer.createUnauthenticatedClient();
        orphanedFolder = OrphanedFolder.create(client.getApisHandler()).get();
    }

    @AfterAll
    static void removeClient() {
        WebClients.removeClient(client);
    }

    @Test
    void Check_Has_Children() {
        boolean expectedChildren = true;

        boolean hasChildren = orphanedFolder.hasChildren();

        Assertions.assertEquals(expectedChildren, hasChildren);
    }

    @Test
    void Check_Children() throws InterruptedException {
        List<? extends RepositoryEntity> expectedChildren = List.of(OmeroServer.getOrphanedImage());

        List<? extends RepositoryEntity> children = orphanedFolder.getChildren();
        while (orphanedFolder.isPopulatingChildren()) {
            TimeUnit.MILLISECONDS.sleep(50);
        }

        TestUtilities.assertCollectionsEqualsWithoutOrder(expectedChildren, children);
    }

    @Test
    void Check_Is_Filtered() {
        boolean isFiltered = orphanedFolder.isFilteredByGroupOwnerName(null, null, null);

        Assertions.assertTrue(isFiltered);
    }
}
