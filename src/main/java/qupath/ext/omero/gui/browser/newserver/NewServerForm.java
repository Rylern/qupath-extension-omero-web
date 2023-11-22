package qupath.ext.omero.gui.browser.newserver;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import qupath.ext.omero.core.ClientsPreferencesManager;
import qupath.ext.omero.gui.UiUtilities;

import java.io.IOException;

/**
 * Window that provides an input allowing the user to write a server URL.
 */
public class NewServerForm extends VBox {

    @FXML
    private TextField url;

    @FXML
    private CheckBox skipAuthentication;

    /**
     * Creates the new server form.
     * @throws IOException if an error occurs while creating the form
     */
    public NewServerForm() throws IOException {
        UiUtilities.loadFXML(this, NewServerForm.class.getResource("new_server_form.fxml"));

        url.setText(ClientsPreferencesManager.getLastServerURI());
    }

    public String getURL() {
        return url.getText();
    }

    public boolean canSkipAuthentication() {
        return skipAuthentication.isSelected();
    }
}
