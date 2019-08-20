package com.github.ustc_zzzz.virtualchest;

import com.google.common.base.Charsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.slf4j.Logger;
import org.spongepowered.plugin.meta.version.ComparableVersion;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author ustc_zzzz
 */
public class VirtualChestPluginUpdate
{
    private boolean check = true;
    private boolean checkPre = true;

    private final Logger logger;
    private final VirtualChestPlugin plugin;

    private static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH);

    public VirtualChestPluginUpdate(VirtualChestPlugin plugin)
    {
        this.logger = plugin.getLogger();
        this.plugin = plugin;
    }

    public void loadConfig(CommentedConfigurationNode node)
    {
        switch (node.getString("true").toLowerCase(Locale.ENGLISH))
        {
        case "prerelease":
        case "true":
        default:
            this.checkPre = true;
            this.check = true;
            break;
        case "release":
        case "false":
            this.checkPre = false;
            this.check = true;
            break;
        case "disabled":
            this.checkPre = false;
            this.check = false;
            break;
        }
        new Thread(this::checkUpdate).start();
    }

    private void checkUpdate()
    {
        try
        {
            if (this.check)
            {
                URL apiUrl = new URL(VirtualChestPlugin.API_URL);
                HttpsURLConnection connection = (HttpsURLConnection) apiUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.getResponseCode();
                InputStreamReader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8);
                JsonArray elements = new JsonParser().parse(reader).getAsJsonArray();
                for (JsonElement element : elements)
                {
                    JsonObject jsonObject = element.getAsJsonObject();
                    String versionString = jsonObject.get("tag_name").getAsString();
                    boolean prerelease = jsonObject.get("prerelease").getAsBoolean();
                    if (versionString.startsWith("v") && (!prerelease || this.checkPre))
                    {
                        String name = jsonObject.get("name").getAsString();
                        String url = jsonObject.get("html_url").getAsString();
                        ComparableVersion version = new ComparableVersion(versionString.substring(1));
                        String date = RFC3339.format(ISO8601.parse(jsonObject.get("published_at").getAsString()));
                        if (version.compareTo(new ComparableVersion(VirtualChestPlugin.VERSION)) > 0)
                        {
                            StringBuilder sb = new StringBuilder("Found new update of VirtualChest.");
                            this.printUpdate(name, url, date, sb);
                            this.logger.warn(sb.toString());
                            break;
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            // <strike>do not bother offline users</strike> maybe bothering them is a better choice
            this.logger.warn("Failed to check update", e);
        }
    }

    private void printUpdate(String name, String url, String date, StringBuilder sb)
    {
        sb.append("\n§e========================================================================");
        sb.append("\n§d       _   __ §c   §e                §a__  __ §b       §9    __ §d       §c__   §e      ");
        sb.append("\n§d      / | / /§c___ §e _      __     §a/ / / /§b____   §9____/ /§d____ _ §c/ /_ §e___    ");
        sb.append("\n§d     /  |/ /§c/ _ \\§e| | /| / /    §a/ / / /§b/ __ \\ §9/ __  /§d/ __ `/§c/ __/§e/ _ \\   ");
        sb.append("\n§d    / /|  /§c/  __/§e| |/ |/ /    §a/ /_/ /§b/ /_/ /§9/ /_/ /§d/ /_/ /§c/ /_ §e/  __/   ");
        sb.append("\n§d   /_/ |_/§c \\___/ §e|__/|__/    §a \\____/§b/ .___/§9 \\__,_/§d \\__,_/§c \\__/§e \\___/    ");
        sb.append("\n§d         §c        §e           §a       §b/_/    §9       §d       §c     §e           ");
        sb.append("\n§e========================================================================");
        sb.append("\n§dFound new update (released at §b").append(date).append("§d): §9").append(name);
        sb.append("\n§dLink: §a").append(url);
        sb.append("\n§e========================================================================");
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        if (this.checkPre)
        {
            node.setValue("prerelease");
        }
        else if (this.check)
        {
            node.setValue("release");
        }
        else
        {
            node.setValue("disabled");
        }
        node.setComment(this.plugin.getTranslation().take("virtualchest.config.checkUpdate.comment").toPlain());
    }
}
