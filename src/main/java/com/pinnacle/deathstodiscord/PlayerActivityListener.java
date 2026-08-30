package com.pinnacle.deathstodiscord;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

final class PlayerActivityListener implements Listener {

    private final KnownPlayerDirectory players;
    private final Runnable deathCallback;

    PlayerActivityListener(KnownPlayerDirectory players, Runnable deathCallback) {
        this.players = players;
        this.deathCallback = deathCallback;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        players.remember(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        players.remember(event.getEntity().getUniqueId(), event.getEntity().getName());
        deathCallback.run();
    }
}
