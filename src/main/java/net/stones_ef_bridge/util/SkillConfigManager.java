package net.stones_ef_bridge.util;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import yesman.epicfight.skill.Skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class SkillConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Eigene .cfg Datei, um Forge's NightConfig zu umgehen!
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("stones_ef_bridge_rarities.cfg");

    private static final Map<String, Integer> CONFIG_MAP = new HashMap<>();
    private static final Set<String> COMMENTED_SKILLS = new HashSet<>();

    private static boolean isInitialized = false;

    public static void initIfNeeded(Iterable<Skill> allSkills) {
        if (isInitialized) return;
        isInitialized = true;

        loadConfig();
        if (allSkills != null) {
            scanAndAppendMissingSkills(allSkills);
        }
    }

    public static void loadConfig() {
        CONFIG_MAP.clear();
        COMMENTED_SKILLS.clear();

        if (!Files.exists(CONFIG_PATH)) {
            createDefaultConfig();
        }

        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Auskommentierte Zeilen speichern, damit sie nicht doppelt angehängt werden
                if (line.startsWith("#")) {
                    String cleanLine = line.substring(1).trim();
                    String[] parts = cleanLine.split("=");
                    if (parts.length == 2) {
                        COMMENTED_SKILLS.add(parts[0].trim().toLowerCase());
                    }
                    continue;
                }

                // Aktive Config-Werte einlesen
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    try {
                        String skillId = parts[0].trim().toLowerCase();
                        int cost = Integer.parseInt(parts[1].trim());
                        CONFIG_MAP.put(skillId, Math.max(1, Math.min(64, cost))); // Clamp zwischen 1 und 64
                    } catch (Exception e) {
                        LOGGER.warn("[Stones-EF-Bridge] Ungültige Config-Zeile übersprungen: " + line);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Stones-EF-Bridge] Fehler beim Laden der Config!", e);
        }
    }

private static void scanAndAppendMissingSkills(Iterable<Skill> skills) {
        List<String> linesToAppend = new ArrayList<>();
        boolean foundNew = false;
        
        // WIR LASSEN DIE REGISTRY-ABFRAGE KOMPLETT WEG!
        // Die ist zu diesem Zeitpunkt (RegisterEvent) noch nicht sicher abrufbar.
        
        for (Skill skill : skills) {
            // Wir nutzen direkt die Methode des Skills, wie in deinem EventHandler!
            ResourceLocation id = skill.getRegistryName(); 
            
            if (id != null) {
                String skillIdStr = id.toString().toLowerCase();

                // Wenn der Skill weder aktiv noch als Kommentar existiert -> Anhängen!
                if (!CONFIG_MAP.containsKey(skillIdStr) && !COMMENTED_SKILLS.contains(skillIdStr)) {
                    if (!foundNew) {
                        linesToAppend.add("");
                        linesToAppend.add("# --- AUTO-DISCOVERED SKILLS ---");
                        linesToAppend.add("# Remove the ‘#’ at the beginning of each line to adjust the cost of a skill.");
                        foundNew = true;
                    }

                    // Deterministisches Jitter-Hashing als Vorschlagswert
                    int hash = Math.abs(skillIdStr.hashCode());
                    int index = hash % 4;
                    int[] cardSteps = {3, 5, 8, 13};
                    int suggestedCost = cardSteps[index];

                    linesToAppend.add("# " + skillIdStr + " = " + suggestedCost);
                    COMMENTED_SKILLS.add(skillIdStr);
                    LOGGER.info("[Stones-EF-Bridge] Neuen Skill in Config gefunden und angehängt: " + skillIdStr);
                }
            }
        }

        if (foundNew) {
            try {
                Files.write(CONFIG_PATH, linesToAppend, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.error("[Stones-EF-Bridge] Konnte neue Skills nicht an die Config anhängen!", e);
            }
        }
    }

	private static void createDefaultConfig() {
        List<String> defaultLines = List.of(
                "# Stones Epic Fight Bridge - Rarities Configuration",
                "# ==================================================",
                "# Manually define the required amount of skill books (cards) for specific skills here.",
                "# This overrides the default automatic hashing system.",
                "#",
                "# Format: modid:skill_name=COST",
                "# Valid values: 1 to 64.",
                "#",
                "# --- Rarity & Rune Level Info ---",
                "# The cost determines both the visual rarity and the Rune Enchantment's base requirement level",
                "# (how much the required socket level increases per enchantment level):",
                "# Cost 1 - 3: Common    (Base Req: 1.0)",
                "# Cost 4 - 5: Uncommon  (Base Req: 2.0)",
                "# Cost 6 - 8: Rare      (Base Req: 3.0)",
                "# Cost 9+:    Legendary (Base Req: 4.0)",
                "#"
        );
        try {
            Files.write(CONFIG_PATH, defaultLines, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            LOGGER.error("[Stones-EF-Bridge] Could not create default config!", e);
        }
    }

    public static Map<String, Integer> getActiveConfig() {
        return CONFIG_MAP;
    }
}