package tech.bedev.discordsrvutils;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.OnlineStatus;
import github.scarsz.discordsrv.dependencies.jda.api.requests.GatewayIntent;
import me.clip.placeholderapi.PlaceholderListener;
import net.md_5.bungee.api.ChatColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import tech.bedev.discordsrvutils.commands.DiscordSRVUtilsCommand;
import tech.bedev.discordsrvutils.commands.tabCompleters.DiscordSRVUtilsTabCompleter;
import tech.bedev.discordsrvutils.events.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DiscordSRVUtils extends JavaPlugin {
    public static boolean PAPI;
    Path databaseFile;
    String jdbcUrl;
    public DiscordSRVEventListener discordListener;
    public JDAEvents JDALISTENER;

    public static JDA getJda() {
        return DiscordSRV.getPlugin().getJda();
    }
    public static Timer timer = new Timer();

    @Override
    public void onEnable() {
        if (!this.getDescription().getName().equals("DiscordSRVUtils")) {
            setEnabled(false);
            System.out.println("[DiscordSRVUtils] Detected plugin name change.");
            return;
        }
        this.saveDefaultConfig();
        this.reloadConfig();
        String storage = "Unknown";
        if (getConfig().getBoolean("MySQL.isEnabled")) {
            storage = "MySQL";
        } else {
            storage = "HSQLDB (local)";
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "" +
                "\n[]=====[&2Enabling DiscordSRVUtils&r]=====[]\n" +
                "| &cInformation:\n&r" +
                "|   &cName: &rDiscordSRVUtils\n&r" +
                "|   &cDeveloper: &rBlue Tree\n&r" +
                "|   &cVersion: &r" + getDescription().getVersion() + "\n&r" +
                "|   &cStorage: &r" + storage + "\n&r" +
                "| &cSupport:\n&r" +
                "|   &cGithub: &rhttps://github.com/BlueTree242/DiscordSRVUtils/issues\n" +
                "|   &cDiscord: &rhttps://discord.gg/MMMQHA4\n" +
                "[]================================[]"));
        System.setProperty("hsqldb.reconfig_logging", "false");
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        if (!getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            getLogger().warning("DiscordSRVUtils could not be enabled. DiscordSRV is not installed or is not enabled.");
            getLogger().warning("We will add support for no discordsrv in the future.");
            setEnabled(false);
            return;
        }
        databaseFile = getDataFolder().toPath().resolve("Database");
        String jdbcUrl = "jdbc:hsqldb:file:" + databaseFile.toAbsolutePath();
        try (Connection conn = getDatabaseFile()) {
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS discordsrvutils_ticket_allowed_roles (TicketID int, RoleID Bigint)").execute();
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS discordsrvutils_tickets (" +
                    "TicketID int, Name Varchar(500), " +
                    "MessageId Bigint, " +
                    "Opened_Category Bigint, " +
                    "Closed_Category Bigint, ChannelID Bigint)").execute();
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS discordsrvutils_Opened_Tickets (UserID Bigint, MessageID Bigint, TicketID Bigint, Channel_id Bigint)").execute();
            conn.prepareStatement("CREATE TABLE IF NOT EXISTS discordsrvutils_Closed_Tickets (UserID Bigint, MessageID Bigint, TicketID Bigint, Channel_id Bigint, Closed_Message Bigint)").execute();
        }
        catch (SQLException exception)  {
            exception.printStackTrace();

        }
        try (Connection conn = getMemoryConnection()) {

            conn.prepareStatement("CREATE TABLE status (Status int)").execute();
            conn.prepareStatement("CREATE TABLE tickets_creating (UserID Bigint, Channel_id Bigint, step int, Name Varchar(500), MessageId Bigint, Opened_Category Bigint, Closed_Category Bigint, TicketID int); ").execute();
            conn.prepareStatement("CREATE TABLE discordsrvutils_ticket_allowed_roles (UserID Bigint, Channel_id Bigint, RoleID Bigint)").execute();
            conn.prepareStatement("CREATE TABLE discordsrvutils_Awaiting_Edits (Channel_id Bigint, UserID Bigint, Type int, MessageID Bigint, TicketID int)").execute();
        }
        catch (SQLException exception) {
            exception.printStackTrace();
        }
        if (getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getServer().getPluginManager().registerEvents(new EssentialsAfk(this), this);
        }
        if (getServer().getPluginManager().isPluginEnabled("AdvancedBan")) {
            getServer().getPluginManager().registerEvents(new AdvancedBanListener(this), this);
        }

        Objects.requireNonNull(getCommand("discordsrvutils")).setExecutor(new DiscordSRVUtilsCommand(this));
        Objects.requireNonNull(getCommand("discordsrvutils")).setTabCompleter(new DiscordSRVUtilsTabCompleter());
        this.discordListener = new DiscordSRVEventListener(this);
        this.JDALISTENER = new JDAEvents(this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinEvent(this), this);

        DiscordSRV.api.subscribe(discordListener);



            if (getConfig().getLong("welcomer_channel") == 0) {
            getLogger().warning("Welcomer messages channel not specified");
        }
        if (DiscordSRV.isReady) {
            getJda().addEventListener(JDALISTENER);
            String status = getConfig().getString("bot_status");
            if (status != null) {
                switch (status.toUpperCase()) {
                    case "DND":
                        getJda().getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
                        break;
                    case "IDLE":
                        getJda().getPresence().setStatus(OnlineStatus.IDLE);
                        break;
                    case "ONLINE":
                        getJda().getPresence().setStatus(OnlineStatus.ONLINE);
                        break;
                }
            }
        }
            new UpdateChecker(this).getVersion(version -> {
                if (this.getDescription().getVersion().equalsIgnoreCase(version.replace("_", " "))) {
                    getLogger().info(ChatColor.GREEN + "No new version available. (" + version.replace("_", " ") + ")");
                } else {
                    getLogger().info(ChatColor.GREEN + "A new version is available. Please update ASAP!" + " Your version: " + ChatColor.YELLOW + this.getDescription().getVersion() + ChatColor.GREEN + " New version: " + ChatColor.YELLOW + version.replace("_", " "));
                }
            });

            int pluginId = 9456; // <-- Replace with the id of your plugin!
            Metrics metrics = new Metrics(this, pluginId);
             PAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            Bukkit.getScheduler().runTask(this, () -> {
                new PlaceholderAPI().register();

            });
                }




    }
    @Override
    public void onLoad() {
        if (!this.getDescription().getName().equals("DiscordSRVUtils")) {
            setEnabled(false);
        }
        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_MESSAGE_REACTIONS);
        }
    }
    public static Connection getDatabase() throws SQLException{
        return new DiscordSRVUtils().getDatabaseFile();
    }
    public Connection getDatabaseFile() throws SQLException {
        if (!this.getConfig().getBoolean("MySQL.isEnabled")) {
            return DriverManager.getConnection("jdbc:hsqldb:file:" + getDataFolder().toPath().resolve("Database") + ";hsqldb.lock_file=false;hsqldb.reconfig_logging=false", "SA", "");
        }
        Connection conn = null;
        Properties connectionProps = new Properties();
        connectionProps.put("user", getConfig().getString("MySQL.UserName"));
        connectionProps.put("password", getConfig().getString("MySQL.Password"));

        if (true) {
            conn = DriverManager.getConnection(
                    "jdbc:" + "mysql" + "://" +
                            getConfig().getString("MySQL.Host") +
                            ":" + getConfig().getInt("MySQL.Port") + "/" + getConfig().getString("MySQL.Database"),
                    connectionProps);
        }
        return conn;
    }
    public Connection getMemoryConnection() throws SQLException{
        return DriverManager.getConnection("jdbc:hsqldb:mem:MemoryDatabase", "SA", "");
    }
    @Override
    public void onDisable() {
        timer.cancel();
    }
}
