package com.github.ustc_zzzz.virtualchest.record;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import org.javalite.activejdbc.DB;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.sql.SqlService;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * @author ustc_zzzz
 */
public class VirtualChestRecordManager
{
    private static final String DEFAULT_JDBC_URL = "jdbc:h2:record";
    private static final String TASK_NAME = "VirtualChestRecordManager";

    static final String OPEN_RECORD = "virtualchest_open_record";
    static final String CLOSE_RECORD = "virtualchest_close_record";
    static final String SLOT_CLICK_RECORD = "virtualchest_slot_click_record";
    static final String ACTION_EXECUTION_RECORD = "virtualchest_action_execution_record";

    private final Logger logger;
    private final VirtualChestPlugin plugin;
    private final VirtualChestTranslation translation;

    private SqlService sql;
    private DataSource dataSource;
    private volatile String databaseUrl = ""; // the record feature will be disabled if the field is empty

    public VirtualChestRecordManager(VirtualChestPlugin plugin)
    {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.translation = plugin.getTranslation();
    }

    public void init()
    {
        this.sql = Sponge.getServiceManager().provideUnchecked(SqlService.class);
        try // TODO: the logger is annoying, so disable it anyway (although it is a dirty way)
        {
            Field field = DB.class.getDeclaredField("LOGGER");
            field.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            int m = field.getModifiers() & ~Modifier.FINAL;
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, m);

            NOPLogger l = NOPLogger.NOP_LOGGER;
            field.set(null, l);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public void recordOpen(UUID uuid, String menuName, Player p)
    {
        if (!this.databaseUrl.isEmpty())
        {
            try (DB db = new DB())
            {
                db.open(this.dataSource);
                new VirtualChestOpenRecord(uuid, menuName, p).insert();
            }
        }
    }

    public void recordClose(UUID uuid, String menuName, Player p)
    {
        if (!this.databaseUrl.isEmpty())
        {
            try (DB db = new DB())
            {
                db.open(this.dataSource);
                new VirtualChestCloseRecord(uuid, menuName, p).insert();
            }
        }
    }

    public void recordSlotClick(UUID uuid, String menuName, int menuSlot, VirtualChestInventory.ClickStatus s, Player p)
    {
        if (!this.databaseUrl.isEmpty())
        {
            try (DB db = new DB())
            {
                db.open(this.dataSource);
                new VirtualChestClickSlotRecord(uuid, menuName, menuSlot, s, p).insert();
            }
        }
    }

    public void recordExecution(UUID uuid, int order, String executionPrefix, String executionSuffix)
    {
        if (!this.databaseUrl.isEmpty())
        {
            try (DB db = new DB())
            {
                db.open(this.dataSource);
                new VirtualChestActionExecutionRecord(uuid, order, executionPrefix, executionSuffix).insert();
            }
        }
    }

    public void loadConfig(CommentedConfigurationNode node)
    {
        this.databaseUrl = "";
        if (node.getNode("enabled").getBoolean(true))
        {
            this.logger.debug("Trying to connect and initialize the database ...");
            String databaseUrl = node.getNode("database-url").getString(DEFAULT_JDBC_URL);
            Task.builder().async().name(TASK_NAME).execute(() -> this.connectDB(databaseUrl)).submit(this.plugin);
        }
    }

    private void connectDB(String databaseUrl)
    {
        try
        {
            Optional<String> aliasOptional = this.sql.getConnectionUrlFromAlias(databaseUrl);
            if (aliasOptional.isPresent())
            {
                this.dataSource = this.sql.getDataSource(aliasOptional.get());
            }
            else
            {
                this.dataSource = this.sql.getDataSource(this.plugin, databaseUrl);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Failed to initialize tables for " + databaseUrl, e);
        }
        try (Connection c = this.dataSource.getConnection())
        {
            c.prepareStatement("CREATE TABLE IF NOT EXISTS " + OPEN_RECORD +
                    "(player_uuid       CHAR(36) NOT NULL," +
                    " player_name       VARCHAR(255) NOT NULL," +
                    " menu_name         VARCHAR(255) NOT NULL," +
                    " submit_uuid       CHAR(36) NOT NULL," +
                    " submit_time       DATETIME NOT NULL," +
                    " PRIMARY KEY       (submit_uuid))").execute();
            c.prepareStatement("CREATE TABLE IF NOT EXISTS " + CLOSE_RECORD +
                    "(player_uuid       CHAR(36) NOT NULL," +
                    " player_name       VARCHAR(255) NOT NULL," +
                    " menu_name         VARCHAR(255) NOT NULL," +
                    " submit_uuid       CHAR(36) NOT NULL," +
                    " submit_time       DATETIME NOT NULL," +
                    " PRIMARY KEY       (submit_uuid))").execute();
            c.prepareStatement("CREATE TABLE IF NOT EXISTS " + SLOT_CLICK_RECORD +
                    "(player_uuid       CHAR(36) NOT NULL," +
                    " player_name       VARCHAR(255) NOT NULL," +
                    " menu_name         VARCHAR(255) NOT NULL," +
                    " menu_slot         INT NOT NULL," +
                    " is_shift          BOOLEAN NOT NULL," +
                    " is_primary        BOOLEAN NOT NULL," +
                    " is_secondary      BOOLEAN NOT NULL," +
                    " submit_uuid       CHAR(36) NOT NULL," +
                    " submit_time       DATETIME NOT NULL," +
                    " PRIMARY KEY       (submit_uuid))").execute();
            c.prepareStatement("CREATE TABLE IF NOT EXISTS " + ACTION_EXECUTION_RECORD +
                    "(submit_uuid       CHAR(36) NOT NULL," +
                    " execution_prefix  VARCHAR(255) NOT NULL," +
                    " execution_suffix  VARCHAR(255) NOT NULL," +
                    " execution_order   INT NOT NULL," +
                    " execution_time    DATETIME NOT NULL," +
                    " PRIMARY KEY       (submit_uuid, execution_order))").execute();
            this.logger.debug("Successfully connected and initialized database.");
            this.databaseUrl = databaseUrl;
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Failed to initialize tables for " + databaseUrl, e);
        }
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        if (this.databaseUrl.isEmpty())
        {
            node.getNode("enabled").setValue(false)
                    .setComment(node.getNode("enabled").getComment().orElse(this.translation
                            .take("virtualchest.config.recording.enabled.comment").toPlain()));
            node.setComment(node.getComment().orElse(this.translation
                    .take("virtualchest.config.recording.comment").toPlain()));
        }
        else
        {
            node.getNode("enabled").setValue(true)
                    .setComment(node.getNode("enabled").getComment().orElse(this.translation
                            .take("virtualchest.config.recording.enabled.comment").toPlain()));
            node.getNode("database-url").setValue(this.databaseUrl)
                    .setComment(node.getNode("database-url").getComment().orElse(this.translation
                            .take("virtualchest.config.recording.databaseUrl.comment").toPlain()));
            node.setComment(node.getComment().orElse(this.translation
                    .take("virtualchest.config.recording.comment").toPlain()));
        }
    }
}
