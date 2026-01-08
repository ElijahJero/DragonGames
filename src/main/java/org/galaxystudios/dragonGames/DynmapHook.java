package org.galaxystudios.dragonGames;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional Dynmap integration via reflection to avoid a hard dependency.
 */
public final class DynmapHook {

    private final DragonGames plugin;

    private Object markerApi;
    private Object markerSet;
    private Object marker;

    private boolean available;

    public DynmapHook(DragonGames plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (!plugin.getConfig().getBoolean("dynmap.enabled", true)) {
            available = false;
            return;
        }
        Plugin dynmap = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmap == null || !dynmap.isEnabled()) {
            available = false;
            return;
        }

        try {
            Method getMarkerAPI = dynmap.getClass().getMethod("getMarkerAPI");
            markerApi = getMarkerAPI.invoke(dynmap);
            if (markerApi == null) {
                available = false;
                return;
            }

            String setId = plugin.getConfig().getString("dynmap.marker-set-id", "dragongames");
            String setLabel = plugin.getConfig().getString("dynmap.marker-set-label", "Dragon Egg");

            Method getMarkerSet = markerApi.getClass().getMethod("getMarkerSet", String.class);
            markerSet = getMarkerSet.invoke(markerApi, setId);

            if (markerSet == null) {
                Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class, String.class, java.util.Set.class, boolean.class);
                markerSet = createMarkerSet.invoke(markerApi, setId, setLabel, null, false);
            }
            available = markerSet != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("Dynmap hook init failed: " + t.getMessage());
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void updateMarker(Player holder) {
        if (!available || holder == null) return;
        Location loc = holder.getLocation();
        if (loc.getWorld() == null) return;

        String markerId = plugin.getConfig().getString("dynmap.marker-id", "dragon-egg-holder");
        String label = plugin.getConfig().getString("dynmap.marker-label", "Dragon Egg Holder");
        String iconId = plugin.getConfig().getString("dynmap.icon", "portal");

        try {
            Method getMarker = markerSet.getClass().getMethod("findMarker", String.class);
            marker = getMarker.invoke(markerSet, markerId);

            Object icon = null;
            try {
                Method getMarkerIcon = markerApi.getClass().getMethod("getMarkerIcon", String.class);
                icon = getMarkerIcon.invoke(markerApi, iconId);
            } catch (Throwable ignored) {}

            if (marker == null) {
                Method createMarker = markerSet.getClass().getMethod(
                        "createMarker",
                        String.class, String.class, String.class,
                        double.class, double.class, double.class,
                        Object.class, boolean.class);
                marker = createMarker.invoke(markerSet, markerId, label, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), icon, false);
            } else {
                Method setLocation = marker.getClass().getMethod("setLocation", String.class, double.class, double.class, double.class);
                setLocation.invoke(marker, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                Method setLabel = marker.getClass().getMethod("setLabel", String.class);
                setLabel.invoke(marker, label + ": " + holder.getName());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Dynmap marker update failed: " + t.getMessage());
        }
    }

    public void clearMarker() {
        if (!available) return;
        try {
            String markerId = plugin.getConfig().getString("dynmap.marker-id", "dragon-egg-holder");
            Method findMarker = markerSet.getClass().getMethod("findMarker", String.class);
            Object m = findMarker.invoke(markerSet, markerId);
            if (m != null) {
                Method deleteMarker = m.getClass().getMethod("deleteMarker");
                deleteMarker.invoke(m);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Dynmap marker clear failed: " + t.getMessage());
        }
    }
}

