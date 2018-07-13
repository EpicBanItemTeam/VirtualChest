package com.github.ustc_zzzz.virtualchest.record;

import com.github.ustc_zzzz.virtualchest.VirtualChestPlugin;
import com.github.ustc_zzzz.virtualchest.inventory.VirtualChestInventory;
import com.github.ustc_zzzz.virtualchest.translation.VirtualChestTranslation;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.Types;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.javalite.activejdbc.DB;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.sql.SqlService;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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

    private FilterMode filterMode = FilterMode.BLACKLIST;
    private List<String> filterRuleList = ImmutableList.of();

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

    public boolean filter(String name, VirtualChestInventory inv)
    {
        return this.filterMode.check(this.filterRuleList, name, inv);
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

    public void loadConfig(CommentedConfigurationNode node) throws IOException
    {
        try
        {
            this.filterMode = node.getNode("filter", "mode").getValue(FilterMode.TYPE_TOKEN, FilterMode.BLACKLIST);
            this.filterRuleList = node.getNode("filter", "rules").getList(Types::asString, ImmutableList.of());
        }
        catch (ObjectMappingException e)
        {
            throw new IOException(e);
        }
        if (node.getNode("enabled").getBoolean(true))
        {
            this.databaseUrl = "";
            this.logger.info("Trying to connect and initialize the database ...");
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
            this.logger.info("Successfully connected and initialized database.");
            this.databaseUrl = databaseUrl;
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Failed to initialize tables for " + databaseUrl, e);
        }
    }

    public void saveConfig(CommentedConfigurationNode node)
    {
        String databaseUrlComment = "virtualchest.config.recording.databaseUrl.comment";
        String rulesComment = "virtualchest.config.recording.filter.rules.comment";
        String modeComment = "virtualchest.config.recording.filter.mode.comment";
        String enabledComment = "virtualchest.config.recording.enabled.comment";
        String filterComment = "virtualchest.config.recording.filter.comment";
        String recordingComment = "virtualchest.config.recording.comment";
        if (this.databaseUrl.isEmpty())
        {
            this.translation.withComment(node.getNode("enabled"), enabledComment).setValue(false);
            this.translation.withComment(node, recordingComment);
        }
        else
        {
            this.translation.withComment(node.getNode("filter", "mode"), modeComment).setValue(this.filterMode.name().toLowerCase());
            this.translation.withComment(node.getNode("filter", "rules"), rulesComment).setValue(this.filterRuleList);
            this.translation.withComment(node.getNode("database-url"), databaseUrlComment).setValue(this.databaseUrl);
            this.translation.withComment(node.getNode("enabled"), enabledComment).setValue(true);
            this.translation.withComment(node.getNode("filter"), filterComment);
            this.translation.withComment(node, recordingComment);
        }
    }

    public enum FilterMode
    {
        WHITELIST, BLACKLIST;

        public static final TypeToken<FilterMode> TYPE_TOKEN = TypeToken.of(FilterMode.class);

        public boolean check(List<String> filterRules, String name, VirtualChestInventory inv)
        {
            switch (this)
            {
            case BLACKLIST:
                return filterRules.stream().noneMatch(name::equals);
            case WHITELIST:
                return filterRules.stream().anyMatch(name::equals);
            default:
                return false;
            }
        }
    }
}
