package qupath.lib.images.servers.omero.common.api.authenticators.gui;

import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.omero.common.api.clients.ClientsPreferencesManager;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ResourceBundle;

/**
 * <p>
 *     {@link java.net.Authenticator Authenticator} that reads username and password information
 *     from a dialog window.
 * </p>
 * <p>
 *     The dialog window is described in {@link AuthenticatorForm}.
 * </p>
 * <p>
 *     Even though this class uses UI elements, it can be called from any thread.
 *     However, make sure the UI thread is not blocked when calling this class, otherwise
 *     it will block the entire application.
 * </p>
 */
public class GuiAuthenticator extends Authenticator {
    private final static ResourceBundle resources = ResourceBundle.getBundle("qupath.lib.images.servers.omero.strings");

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        AuthenticatorForm authenticatorForm = new AuthenticatorForm(getRequestingPrompt(), getRequestingHost(), ClientsPreferencesManager.getLastUsername());
        boolean loginConfirmed = Dialogs.showConfirmDialog(resources.getString("Common.Api.Authenticator.login"), authenticatorForm);

        if (loginConfirmed) {
            PasswordAuthentication authentication = new PasswordAuthentication(authenticatorForm.getUsername(), authenticatorForm.getPassword());
            ClientsPreferencesManager.setLastUsername(authentication.getUserName());
            return authentication;
        } else {
            return null;
        }
    }
}
