package LocalSinglePlayerLogin;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.JagString;
import rt4.LoginManager;
import rt4.client;

@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Automatically enters the prepared local single-player profile.",
        version = 1.0
)
public class plugin extends Plugin {
    private boolean attempted = false;

    @Override
    public void ComponentDraw(int componentIndex, Component component, int screenX, int screenY) {
        if (attempted || API.IsLoggedIn() || component == null || component.text == null) {
            return;
        }
        if (client.gameState != 10 || !component.text.equals(JagString.of("Please Log In"))) {
            return;
        }

        String username = System.getProperty("singlePlayerName", "Player").trim();
        String password = System.getProperty("singlePlayerPassword", "local");
        if (username.length() == 0) {
            username = "Player";
        }
        if (password.length() == 0) {
            password = "local";
        }

        API.SetVarcStr(32, username);
        API.SetVarcStr(33, password);
        LoginManager.method3896(JagString.of(username), JagString.of(password), 0);
        attempted = true;
    }

    @Override
    public void OnLogout() {
        attempted = false;
    }
}
