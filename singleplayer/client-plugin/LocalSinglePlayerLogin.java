package LocalSinglePlayerLogin;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.JagString;
import rt4.LoginManager;
import rt4.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Automatically enters the prepared local single-player profile.",
        version = 1.1
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

        String username = loadProfileName();
        String password = "local";

        API.SetVarcStr(32, username);
        API.SetVarcStr(33, password);
        LoginManager.method3896(JagString.of(username), JagString.of(password), 0);
        attempted = true;
    }

    private String loadProfileName() {
        String direct = System.getProperty("singlePlayerName", "").trim();
        if (direct.length() > 0) return direct;

        String home = System.getProperty("clientHomeOverride", "");
        if (home.length() > 0) {
            File profile = new File(home, "singleplayer-profile.txt");
            if (profile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(profile))) {
                    String line = reader.readLine();
                    if (line != null && line.trim().length() > 0) {
                        return line.trim();
                    }
                } catch (Exception ignored) { }
            }
        }
        return "Player";
    }

    @Override
    public void OnLogout() {
        attempted = false;
    }
}
