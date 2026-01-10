package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

/**
 * Optional Dynmap integration using the Dynmap API (no hard dependency at runtime).
 */
public final class DynmapHook {

    private final DragonGames plugin;

    private DynmapCommonAPI api;
    private MarkerAPI markerApi;
    private MarkerSet markerSet;
    private Marker marker;

    private boolean available;
    private boolean listenerRegistered;

    private Location lastKnownLoc;
    private String lastHolderName;

    public DynmapHook(DragonGames plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (!plugin.getConfig().getBoolean("dynmap.enabled", true)) {
            available = false;
            return;
        }
        registerListenerOnce();

        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap instanceof DynmapCommonAPI dynmapApi && dynmap.isEnabled()) {
            onApiEnabled(dynmapApi);
        }
    }

    private void registerListenerOnce() {
        if (listenerRegistered) return;
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                onApiEnabled(api);
                refreshLastMarker();
            }

            @Override
            public void apiDisabled(DynmapCommonAPI api) {
                onApiDisabled();
            }
        });
        listenerRegistered = true;
    }

    private void onApiEnabled(DynmapCommonAPI api) {
        this.api = api;
        this.markerApi = api.getMarkerAPI();
        if (markerApi == null) {
            available = false;
            return;
        }

        String setId = plugin.getConfig().getString("dynmap.marker-set-id", "dragongames");
        String setLabel = plugin.getConfig().getString("dynmap.marker-set-label", "Dragon Egg");

        markerSet = markerApi.getMarkerSet(setId);
        if (markerSet == null) {
            markerSet = markerApi.createMarkerSet(setId, setLabel, null, false);
        }
        available = markerSet != null;
    }

    private void onApiDisabled() {
        available = false;
        marker = null;
        markerSet = null;
        markerApi = null;
        api = null;
    }

    public boolean isAvailable() {
        return available;
    }

    public void updateMarker(Player holder) {
        if (holder == null) return;
        lastKnownLoc = holder.getLocation().clone();
        lastHolderName = holder.getName();
        if (!available || lastKnownLoc.getWorld() == null) return;
        upsertMarker(lastKnownLoc, lastHolderName);
    }

    public void refreshLastMarker() {
        if (!available || lastKnownLoc == null || lastKnownLoc.getWorld() == null) return;
        upsertMarker(lastKnownLoc, lastHolderName == null ? "Unknown" : lastHolderName);
    }

    private void upsertMarker(Location loc, String holderName) {
        if (!available || markerSet == null || markerApi == null || loc.getWorld() == null) return;

        String markerId = plugin.getConfig().getString("dynmap.marker-id", "dragon-egg-holder");
        String labelBase = plugin.getConfig().getString("dynmap.marker-label", "Dragon Egg Holder");
        String iconId = plugin.getConfig().getString("dynmap.icon", "portal");

        MarkerIcon icon = markerApi.getMarkerIcon(iconId);
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        String fullLabel = holderName == null ? labelBase : labelBase + ": " + holderName;

        marker = markerSet.findMarker(markerId);
        if (marker == null) {
            marker = markerSet.createMarker(markerId, fullLabel, world, x, y, z, icon, false);
        } else {
            marker.setLocation(world, x, y, z);
            marker.setLabel(fullLabel);
            if (icon != null) {
                marker.setMarkerIcon(icon);
            }
        }
    }

    public void clearMarker() {
        if (!available || markerSet == null) {
            marker = null;
            lastKnownLoc = null;
            lastHolderName = null;
            return;
        }
        try {
            String markerId = plugin.getConfig().getString("dynmap.marker-id", "dragon-egg-holder");
            Marker m = markerSet.findMarker(markerId);
            if (m != null) {
                m.deleteMarker();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Dynmap marker clear failed: " + t.getMessage());
        } finally {
            marker = null;
            lastKnownLoc = null;
            lastHolderName = null;
        }
    }
}
