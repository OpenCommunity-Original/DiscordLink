package com.github.riku32.discordlink.spigot;

import com.github.riku32.discordlink.core.DiscordLink;
import com.github.riku32.discordlink.core.eventbus.EventBus;
import com.github.riku32.discordlink.core.platform.PlatformPlugin;
import com.github.riku32.discordlink.core.platform.PlatformPlayer;
import com.github.riku32.discordlink.core.platform.command.CompiledCommand;
import com.github.riku32.discordlink.spigot.events.MainListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DiscordLinkSpigot extends JavaPlugin implements PlatformPlugin {
    private DiscordLink discordLink;
    private EventBus eventBus;
    private SpigotCommand commandManager;
    private PlayerRegistry playerRegistry;

    @Override
    public void onEnable() {
        this.playerRegistry = new PlayerRegistry();
        getServer().getPluginManager().registerEvents(playerRegistry, this);

        this.eventBus = new EventBus(getLogger());
        getServer().getPluginManager().registerEvents(new MainListener(eventBus, playerRegistry), this);

        this.commandManager = new SpigotCommand(this, playerRegistry);

        // This should automatically create and register the platform plugin
        discordLink = new DiscordLink(this);
        commandManager.setLocale(discordLink.getLocale());

        // Register command after initialization
        PluginCommand mainCommand = Objects.requireNonNull(this.getCommand("discord"));
        mainCommand.setExecutor(commandManager);
        mainCommand.setTabCompleter(commandManager);
    }

    @Override
    public PlatformPlayer getPlayer(UUID uuid) {
        Player player = this.getServer().getPlayer(uuid);
        return player == null ? null : playerRegistry.getPlayer(player);
    }

    @Override
    public PlatformPlayer getPlayer(String username) {
        Player player = this.getServer().getPlayer(username);
        return player == null ? null : playerRegistry.getPlayer(player);
    }

    @Override
    public Set<PlatformPlayer> getPlayers() {
        return getServer().getOnlinePlayers().stream().map(playerRegistry::getPlayer)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NotNull Logger getLogger() {
        return super.getLogger();
    }

    @Override
    public File getDataDirectory() {
        return getDataFolder();
    }

    @Override
    public void disable() {
        if (discordLink != null) discordLink.disable(true);

        Bukkit.getPluginManager().disablePlugin(this);
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void registerCommand(CompiledCommand compiledCommand) {
        this.commandManager.addCommand(compiledCommand);
    }
}
