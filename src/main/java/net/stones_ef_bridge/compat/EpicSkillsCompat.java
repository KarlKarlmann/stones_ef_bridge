package net.stones_ef_bridge.compat;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.stones_ef_bridge.util.SkillRarityManager;
import yesman.epicfight.skill.Skill;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registriert die Forge Konfiguration für die Bridge
 * und generiert eine dynamische Dokumentation aller gefundenen Skills im Config-File.
 */
public class EpicSkillsCompat {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_RARITIES;

    static {
        BUILDER.push("Rarities");

        CUSTOM_RARITIES = BUILDER
                .comment("Definiere hier manuell die benötigte Kartenanzahl für bestimmte Skills (Überschreibt das automatische Hashing).",
                         "Format: 'modid:skill_name=REZEPTKOSTEN' (z.B. 'epicfight:meteor_strike=13').",
                         "Gültige Werte: 1 bis 64. Werte über 10 werden im Spiel als legendär dargestellt.")
                .defineListAllowEmpty("customRarities", new ArrayList<>(), obj -> obj instanceof String);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Liest die Forge-Config-Datei ein, filtert alte Kommentarblöcke heraus und injiziert
     * eine tagesaktuelle, sortierte Liste aller registrierten Epic Fight Skills direkt als Kommentare!
     */
    public static void updateConfigWithDetectedSkills(Iterable<Skill> skills) {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("stones_ef_bridge-common.toml");
            if (!Files.exists(configPath)) {
                // Falls die Datei noch nicht existiert, lassen wir Forge sie beim ersten Start normal generieren.
                return;
            }

            List<String> lines = Files.readAllLines(configPath);
            List<String> newLines = new ArrayList<>();
            boolean inBlock = false;

            // Altes Dokumentations-Segment filtern
            for (String line : lines) {
                if (line.contains("=== ALL DETECTED SKILLS START ===")) {
                    inBlock = true;
                    continue;
                }
                if (line.contains("=== ALL DETECTED SKILLS END ===")) {
                    inBlock = false;
                    continue;
                }
                if (!inBlock) {
                    newLines.add(line);
                }
            }

            // Neues, aktuelles Dokumentations-Segment anfügen
            newLines.add("");
            newLines.add("# ===========================================================================");
            newLines.add("# === ALL DETECTED SKILLS START ===");
            newLines.add("# Hier sind alle im Spiel gefundenen Skills aufgelistet, die du konfigurieren kannst.");
            newLines.add("# Kopiere einfach eine ID und füge sie oben unter 'customRarities' ein.");
            newLines.add("# Beispiel: customRarities = [\"epicfight:roll=3\", \"wom:dodge_master=12\"]");
            newLines.add("# ===========================================================================");
            newLines.add("#");

            // FIX: "epicfight:skills" zu "epicfight:skill" korrigiert
            var skillRegistry = net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(new ResourceLocation("epicfight", "skill"));
            if (skillRegistry != null) {
                for (Skill skill : skills) {
                    ResourceLocation id = skillRegistry.getKey(skill);
                    if (id != null) {
                        newLines.add("# - " + id.toString());
                    }
                }
            }

            newLines.add("#");
            newLines.add("# === ALL DETECTED SKILLS END ===");

            Files.write(configPath, newLines);
            SkillRarityManager.markCacheDirty(); // Cache zur Sicherheit invalidieren
		} catch (Exception e) {
			//stones_ef_bridge.LOGGER.warn("[Stones-EF-Bridge] Konnte die Skill-Dokumentation nicht in die Config schreiben. Möglicherweise fehlende Dateirechte.", e);
		}
    }
}