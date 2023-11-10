package qupath.ext.omero.core.entities.serverinformation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

public class TestOmeroServerList {

    @Test
    void Check_Empty() {
        OmeroServerList omeroServerList = new Gson().fromJson("{}", OmeroServerList.class);

        OptionalInt latestVersionURL = omeroServerList.getServerId();

        Assertions.assertTrue(latestVersionURL.isEmpty());
    }

    @Test
    void Check_Server_Id() {
        OmeroServerList omeroServerList = createOmeroServerList();

        int serverId = omeroServerList.getServerId().orElse(-1);

        Assertions.assertEquals(4, serverId);
    }

    @Test
    void Check_Server_Host() {
        OmeroServerList omeroServerList = createOmeroServerList();

        String serverHost = omeroServerList.getServerHost().orElse("");

        Assertions.assertEquals("53.128.89.34", serverHost);
    }

    @Test
    void Check_Server_Port() {
        OmeroServerList omeroServerList = createOmeroServerList();

        int serverId = omeroServerList.getServerPort().orElse(-1);

        Assertions.assertEquals(50000, serverId);
    }

    private OmeroServerList createOmeroServerList() {
        String json = """
                {
                    "data": [
                        {
                            "id": 4,
                            "host": 53.128.89.34,
                            "port": 50000
                        }
                    ]
                }
                """;
        return new Gson().fromJson(json, OmeroServerList.class);
    }
}
