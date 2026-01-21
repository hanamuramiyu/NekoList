package hanamuramiyu.pawkin.net.discord;

import hanamuramiyu.pawkin.net.core.NekoList;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DiscordBot extends ListenerAdapter {
    private final NekoList plugin;
    private final Map<String, Object> config;
    private JDA jda;
    private boolean starting = false;
    private boolean stopping = false;
    private final ConcurrentHashMap<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cooldownCleaner = Executors.newSingleThreadScheduledExecutor();
    private static final long COMMAND_COOLDOWN_MS = 2000;
    private static final long COOLDOWN_CLEANUP_INTERVAL = 300000;
    
    public DiscordBot(NekoList plugin, Map<String, Object> config) {
        this.plugin = plugin;
        this.config = config;
        startCooldownCleaner();
    }
    
    private void startCooldownCleaner() {
        cooldownCleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            commandCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > COMMAND_COOLDOWN_MS);
        }, COOLDOWN_CLEANUP_INTERVAL, COOLDOWN_CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    public boolean startBot() {
        if (starting || jda != null) {
            return false;
        }
        
        starting = true;
        try {
            String token = (String) config.get("discord-bot.token");
            if (!isValidToken(token)) {
                getLogger().severe("Invalid Discord bot token!");
                starting = false;
                return false;
            }
            
            jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.NONE)
                .disableCache(CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.VOICE_STATE)
                .setEnableShutdownHook(false)
                .addEventListeners(this)
                .build();
            
            jda.awaitReady();
            
            jda.updateCommands().addCommands(getSlashCommands()).queue();
            
            starting = false;
            return true;
        } catch (InvalidTokenException e) {
            getLogger().severe("Invalid Discord token!");
            starting = false;
            return false;
        } catch (InterruptedException e) {
            getLogger().severe("Discord bot interrupted");
            Thread.currentThread().interrupt();
            starting = false;
            return false;
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            starting = false;
            return false;
        }
    }
    
    private boolean isValidToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        if (token.equals("YOUR_BOT_TOKEN_HERE")) {
            return false;
        }
        if (token.length() < 50) {
            return false;
        }
        if (!token.matches("[A-Za-z0-9_.-]+")) {
            return false;
        }
        return true;
    }
    
    public void stopBot() {
        if (stopping || jda == null) {
            return;
        }
        
        stopping = true;
        try {
            cooldownCleaner.shutdown();
            if (!cooldownCleaner.awaitTermination(5, TimeUnit.SECONDS)) {
                cooldownCleaner.shutdownNow();
            }
            
            if (jda != null) {
                jda.shutdown();
                jda = null;
            }
        } catch (Exception e) {
            getLogger().warning("Error while shutting down Discord bot: " + e.getMessage());
        } finally {
            stopping = false;
            starting = false;
            commandCooldowns.clear();
        }
    }
    
    public boolean isRunning() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }
    
    public NekoList getPlugin() {
        return plugin;
    }
    
    public Map<String, Object> getPluginConfig() {
        return config;
    }
    
    private Logger getLogger() {
        return Logger.getLogger("NekoList");
    }
    
    private List<CommandData> getSlashCommands() {
        String pingDescription = plugin.getMessage("discord.ping-description");
        String whitelistDescription = plugin.getMessage("discord.whitelist-description");
        String addDescription = plugin.getMessage("discord.add-description");
        String removeDescription = plugin.getMessage("discord.remove-description");
        String listDescription = plugin.getMessage("discord.list-description");
        String statusDescription = plugin.getMessage("discord.status-description");
        String reloadDescription = plugin.getMessage("discord.reload-description");
        
        return List.of(
            Commands.slash("ping", pingDescription),
            Commands.slash("whitelist", whitelistDescription)
                .addSubcommands(
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("add", addDescription)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", plugin.getMessage("discord.player-option-description"), true),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("remove", removeDescription)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", plugin.getMessage("discord.player-option-description"), true),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("list", listDescription),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("status", statusDescription),
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("reload", reloadDescription)
                )
        );
    }
    
    private String cleanMessage(String message) {
        if (message == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
        );
    }
    
    private boolean checkCooldown(String userId) {
        long now = System.currentTimeMillis();
        Long lastUsed = commandCooldowns.get(userId);
        if (lastUsed != null && (now - lastUsed) < COMMAND_COOLDOWN_MS) {
            return false;
        }
        commandCooldowns.put(userId, now);
        return true;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        
        String userId = event.getUser().getId();
        
        if (!checkCooldown(userId)) {
            event.reply("Please wait before using another command.").setEphemeral(true).queue();
            return;
        }
        
        List<String> allowedUsers = new ArrayList<>();
        List<String> allowedRoles = new ArrayList<>();
        
        try {
            Object usersObj = config.get("discord-bot.allowed-users");
            Object rolesObj = config.get("discord-bot.allowed-roles");
            
            if (usersObj instanceof List) {
                for (Object item : (List<?>) usersObj) {
                    if (item != null) {
                        allowedUsers.add(String.valueOf(item));
                    }
                }
            }
            
            if (rolesObj instanceof List) {
                for (Object item : (List<?>) rolesObj) {
                    if (item != null) {
                        allowedRoles.add(String.valueOf(item));
                    }
                }
            }
        } catch (Exception e) {
        }
        
        boolean hasPermission = false;
        
        if (allowedUsers.contains(userId)) {
            hasPermission = true;
        }
        
        if (!hasPermission && event.getMember() != null) {
            for (String roleId : allowedRoles) {
                if (event.getMember().getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                    hasPermission = true;
                    break;
                }
            }
        }
        
        if (!hasPermission) {
            event.reply(cleanMessage(plugin.getMessage("discord.no-permission"))).setEphemeral(true).queue();
            return;
        }
        
        if (event.getName().equals("ping")) {
            long time = System.currentTimeMillis();
            event.reply(cleanMessage(plugin.getMessage("discord.ping"))).setEphemeral(true).queue(response -> {
                response.editOriginal(cleanMessage(plugin.getMessage("discord.pong").replace("%d", String.valueOf(System.currentTimeMillis() - time)))).queue();
            });
        } else if (event.getName().equals("whitelist")) {
            if (event.getSubcommandName() == null) {
                event.reply(cleanMessage(plugin.getMessage("discord.whitelist-usage"))).setEphemeral(true).queue();
                return;
            }
            
            switch (event.getSubcommandName()) {
                case "add":
                    net.dv8tion.jda.api.interactions.commands.OptionMapping playerOptionAdd = event.getOption("player");
                    if (playerOptionAdd == null) {
                        event.reply("Player option is required.").setEphemeral(true).queue();
                        return;
                    }
                    String playerToAdd = playerOptionAdd.getAsString();
                    if (!isValidPlayerName(playerToAdd)) {
                        event.reply(cleanMessage(plugin.getMessage("errors.invalid-name"))).setEphemeral(true).queue();
                        return;
                    }
                    if (plugin.isPlayerWhitelisted(playerToAdd)) {
                        event.reply(cleanMessage(plugin.getMessage("player-already-whitelisted").replace("%player%", playerToAdd))).setEphemeral(true).queue();
                    } else {
                        plugin.addPlayerToWhitelist(playerToAdd);
                        event.reply(cleanMessage(plugin.getMessage("player-added").replace("%player%", playerToAdd))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "remove":
                    net.dv8tion.jda.api.interactions.commands.OptionMapping playerOptionRemove = event.getOption("player");
                    if (playerOptionRemove == null) {
                        event.reply("Player option is required.").setEphemeral(true).queue();
                        return;
                    }
                    String playerToRemove = playerOptionRemove.getAsString();
                    if (!plugin.isPlayerWhitelisted(playerToRemove)) {
                        event.reply(cleanMessage(plugin.getMessage("player-not-whitelisted").replace("%player%", playerToRemove))).setEphemeral(true).queue();
                    } else {
                        plugin.removePlayerFromWhitelist(playerToRemove);
                        event.reply(cleanMessage(plugin.getMessage("player-removed").replace("%player%", playerToRemove))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "list":
                    java.util.Set<String> players = plugin.getWhitelistedPlayers();
                    if (players.isEmpty()) {
                        event.reply(cleanMessage(plugin.getMessage("whitelist-empty"))).setEphemeral(true).queue();
                    } else {
                        String playerList = String.join(", ", players);
                        event.reply(cleanMessage(plugin.getMessage("whitelist-list").replace("%players%", playerList))).setEphemeral(true).queue();
                    }
                    break;
                    
                case "status":
                    String statusMessage = plugin.isWhitelistEnabled() ? 
                        cleanMessage(plugin.getMessage("whitelist-enabled")) : 
                        cleanMessage(plugin.getMessage("whitelist-disabled"));
                    event.reply(statusMessage).setEphemeral(true).queue();
                    break;
                    
                case "reload":
                    plugin.reloadNekoListConfig();
                    event.reply(cleanMessage(plugin.getMessage("reload-success"))).setEphemeral(true).queue();
                    break;
            }
        }
    }
    
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_]{1,16}$");
    }
    
    @Override
    public void onShutdown(ShutdownEvent event) {
        stopping = false;
        starting = false;
        jda = null;
        commandCooldowns.clear();
    }
}