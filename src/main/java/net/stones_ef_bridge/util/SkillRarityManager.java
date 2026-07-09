package net.stones_ef_bridge.util;

import net.minecraft.world.entity.player.Player;
import net.stones_ef_bridge.StonesEfBridge;
import yesman.epicfight.skill.Skill;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Bestimmt die Seltenheit und Rezeptanforderungen von Epic-Fight-Fähigkeiten.
 * Arbeitet komplett String-basiert und unterstützt Live-Netzwerk-Synchronisation,
 * um Desynchronisationen zwischen Server und Client vollständig auszuschließen.
 */
public class SkillRarityManager {

    public static class RarityInfo {
        public final String displayName;
        public final int requiredCards;
        public final int color;

        public RarityInfo(String displayName, int requiredCards, int color) {
            this.displayName = displayName;
            this.requiredCards = requiredCards;
            this.color = color;
        }
    }

    private static final Map<String, Integer> CONFIG_CACHE = new HashMap<>();
    private static final Map<String, Integer> CLIENT_SYNCHRONIZED_CACHE = new HashMap<>();
    private static boolean cacheDirty = true;

    public static void markCacheDirty() {
        cacheDirty = true;
    }

    /**
     * Setzt die vom Server empfangenen Konfigurationen auf dem Client.
     */
    public static void setSynchronizedConfigs(Map<String, Integer> configs) {
        CLIENT_SYNCHRONIZED_CACHE.clear();
        CLIENT_SYNCHRONIZED_CACHE.putAll(configs);
        StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Server-Config erfolgreich mit dem Client synchronisiert! Einträge: {}", CLIENT_SYNCHRONIZED_CACHE.size());
    }

    /**
     * Setzt die synchronisierten Server-Konfigurationen beim Verlassen des Servers zurück.
     */
    public static void clearSynchronizedConfigs() {
        CLIENT_SYNCHRONIZED_CACHE.clear();
        StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Server-Verbindung getrennt. Client-Config-Cache zurückgesetzt.");
    }

    /**
     * Gibt den rohen Konfigurations-Cache des Servers für den Netzwerk-Versand zurück.
     */
    public static Map<String, Integer> getRawConfigCache() {
        if (cacheDirty) {
            rebuildCache();
        }
        return new HashMap<>(CONFIG_CACHE);
    }

	private static void rebuildCache() {
        CONFIG_CACHE.clear();
        try {
            // Lade die aktuelle Config aus unserer .cfg Datei
            SkillConfigManager.loadConfig();
            
            // Übernimm alle aktiven Einträge in den Cache
            CONFIG_CACHE.putAll(SkillConfigManager.getActiveConfig());
            
        } catch (Throwable t) {
            StonesEfBridge.LOGGER.error("[Stones-EF-Bridge] Fehler beim Laden der Seltenheits-Config!", t);
        }
        cacheDirty = false;
        StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Seltenheits-Config erfolgreich eingelesen! Einträge: {}", CONFIG_CACHE.size());
    }

    public static RarityInfo getRarityInfo(int cards) {
        if (cards <= 3) {
            return new RarityInfo("§fCommon", cards, 0xAAAAAA);
        } else if (cards <= 5) {
            return new RarityInfo("§aUncommon", cards, 0x55FF55);
        } else if (cards <= 8) {
            return new RarityInfo("§9Rare", cards, 0x5555FF);
        } else {
            return new RarityInfo("§6Legendary", cards, 0xFFAA00);
        }
    }

    /**
     * Bestimmt die Kartenanzahl. Priorisiert den vom Server synchronisierten Cache,
     * falls wir auf einem Server spielen. Dadurch sind Client und Server immer absolut synchron.
     */
    public static int getCardCost(String skillIdStr) {
        if (skillIdStr == null) {
            return 3;
        }
        
        String skillKey = skillIdStr.toLowerCase();

        // 1. SPIELER IST AUF EINEM MULTIPLAYER-SERVER: Server-Cache erzwingen
        if (!CLIENT_SYNCHRONIZED_CACHE.isEmpty()) {
            if (CLIENT_SYNCHRONIZED_CACHE.containsKey(skillKey)) {
                return CLIENT_SYNCHRONIZED_CACHE.get(skillKey);
            }
            // Falls der Cache aktiv ist, aber dieser Skill nicht überschrieben wurde,
            // nutzen wir direkt das Standard-Hashing, um lokale Client-Overrides zu ignorieren!
        } else {
            // 2. SPIELER IST IM SINGLEPLAYER: Lokalen TOML-Cache nutzen
            if (cacheDirty) {
                rebuildCache();
            }
            if (CONFIG_CACHE.containsKey(skillKey)) {
                return CONFIG_CACHE.get(skillKey);
            }
        }
        
        // Deterministisches Jitter-Hashing als Fallback
        int hash = Math.abs(skillKey.hashCode());
        int index = hash % 4;
        int[] cardSteps = {3, 5, 8, 13};

        return cardSteps[index];
    }

    public static int getCardCost(Skill skill) {
        if (skill == null) {
            return 3;
        }
        try {
            ResourceLocation rl = skill.getRegistryName();
            if (rl != null) {
                return getCardCost(rl.toString());
            }
        } catch (Throwable ignored) {}
        
        try {
            String str = skill.toString();
            if (str != null) {
                return getCardCost(str);
            }
        } catch (Throwable ignored) {}
        
        return 3;
    }

    public static RarityInfo getRarity(Skill skill, @Nullable Player player) {
        int cost = getCardCost(skill);
        return getRarityInfo(cost);
    }
}