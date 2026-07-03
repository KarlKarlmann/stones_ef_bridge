package net.stones_ef_bridge.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n; // Ermöglicht clientseitige Existenzprüfung von Übersetzungen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.stones_ef_bridge.util.SkillRarityManager;
import yesman.epicfight.api.data.reloader.SkillManager;
import yesman.epicfight.skill.Skill;

import java.util.List;
import java.util.Locale;

/**
 * Rendert das Skill-Bild und dessen Informationen im hochauflösenden Stil einer
 * Divination Card (Sammelkarte) mit automatischem Zeilenumbruch und flexiblem Lore-/Flavortext.
 */
public class ClientSkillTooltip implements ClientTooltipComponent, TooltipComponent {
    private final String skillRegistryName;
    private final ResourceLocation texture;
    private final int currentCount;

    // --- VISUELLE DESIGN-TOKENS ---
    private static final int CARD_WIDTH = 100;     // Feste Breite der Sammelkarte
    private static final int IMAGE_SIZE = 84;      // Quadratische Bildgröße
    private static final int CARD_PADDING = 6;     // Äußerer Abstand der Elemente

	public ClientSkillTooltip(String skillRegistryName, ItemStack stack) {
        this.skillRegistryName = skillRegistryName;
        this.currentCount = stack.getCount();
        
        Skill skill = SkillManager.getSkill(skillRegistryName);
        ResourceLocation baseTexture;

        if (skill != null) {
            // Sicher: Das holt das Bild exakt da ab, wo der Addon-Macher es registriert hat
            baseTexture = skill.getSkillTexture();
        } else {
            // Nur falls der Skill wirklich nicht existiert
            ResourceLocation id = new ResourceLocation(skillRegistryName);
            baseTexture = new ResourceLocation(id.getNamespace(), "textures/gui/skills/" + id.getPath() + ".png");
        }

        // --- DYNAMISCHE KARTEN-PRÜFUNG ---
        String path = baseTexture.getPath();
        ResourceLocation cardTexture;

        if (path.endsWith(".png")) {
            cardTexture = new ResourceLocation(baseTexture.getNamespace(), path.substring(0, path.length() - 4) + "_card.png");
        } else {
            cardTexture = new ResourceLocation(baseTexture.getNamespace(), path + "_card");
        }

        if (Minecraft.getInstance().getResourceManager().getResource(cardTexture).isPresent()) {
            this.texture = cardTexture;
        } else {
            this.texture = baseTexture;
        }
    }

    @Override
    public int getWidth(Font font) {
        return CARD_WIDTH; // Erzwingt die feste, schlanke Kartenbreite im Tooltip
    }

    @Override
    public int getHeight() {
        Font font = Minecraft.getInstance().font;
        
        // Dynamische Höhenberechnung basierend auf den Zeilenumbrüchen
        Component titleComp = getTitleComponent();
        int titleHeight = wrapText(font, titleComp, CARD_WIDTH - 12).size() * 10;
        
        int categoryHeight = 10; // Feste Höhe für die Kategorie-Zeile ("• Passive •")
        int imgHeight = IMAGE_SIZE + 6; // Bildhöhe inklusive Rand/Abstand
        
        Component flavorComp = getFlavorTextComponent();
        int flavorHeight = wrapText(font, flavorComp, CARD_WIDTH - 12).size() * 9;
        
        return CARD_PADDING + titleHeight + categoryHeight + imgHeight + flavorHeight + CARD_PADDING;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int totalHeight = getHeight();
        int requiredCards = SkillRarityManager.getCardCost(this.skillRegistryName);
        SkillRarityManager.RarityInfo rarity = SkillRarityManager.getRarityInfo(requiredCards);

        // --- 1. KARTEN-HINTERGRUND (Visuelle Tiefe) ---
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + totalHeight, 0xFF0D0D12); // Obsidian-Schwarz

        // --- 2. RARITÄTS-BORDER (Dünner Rahmen um die gesamte Karte) ---
        int borderCol = rarity.color | 0xFF000000;
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + 1, borderCol);                      // Oben
        guiGraphics.fill(x, y + totalHeight - 1, x + CARD_WIDTH, y + totalHeight, borderCol); // Unten
        guiGraphics.fill(x, y, x + 1, y + totalHeight, borderCol);                      // Links
        guiGraphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + totalHeight, borderCol); // Rechts

        int currentY = y + CARD_PADDING;

        // --- 3. TITEL (Zentriert & Mehrzeilig umbrechend) ---
        Component titleComp = getTitleComponent();
        List<FormattedCharSequence> titleLines = wrapText(font, titleComp, CARD_WIDTH - 12);
        for (FormattedCharSequence line : titleLines) {
            int lineWidth = font.width(line);
            int lineX = x + (CARD_WIDTH - lineWidth) / 2;
            guiGraphics.drawString(font, line, lineX, currentY, rarity.color, true);
            currentY += 10;
        }

        // --- 4. SUBTITLE / KATEGORIE (z.B. "• Passive •") ---
        Skill skill = SkillManager.getSkill(this.skillRegistryName);
        String categoryName = skill != null && skill.getCategory() != null 
                ? "• " + capitalize(skill.getCategory().toString()) + " •" 
                : "• Skill •";
        int catWidth = font.width(categoryName);
        int catX = x + (CARD_WIDTH - catWidth) / 2;
        guiGraphics.drawString(font, categoryName, catX, currentY, 0xFF777777, false);
        currentY += 10;

        // --- 5. BILD & ARTWORK ---
        int imgX = x + (CARD_WIDTH - IMAGE_SIZE) / 2;
        int imgY = currentY;

        // Dunkler Bildrahmen
        guiGraphics.fill(imgX - 1, imgY - 1, imgX + IMAGE_SIZE + 1, imgY + IMAGE_SIZE + 1, 0xFF353535);

        RenderSystem.enableBlend();
        guiGraphics.blit(this.texture, imgX, imgY, IMAGE_SIZE, IMAGE_SIZE, 0, 0, 128, 128, 128, 128);
        RenderSystem.disableBlend();

        // --- 6. DIVINATION-CARD BADGE (Unten links überstehend) ---
        String stackText = this.currentCount + "/" + requiredCards;
        int textW = font.width(stackText);
        
        int badgeX = imgX - 3;
        int badgeY = imgY + IMAGE_SIZE - 12;
        int badgeW = textW + 5;
        int badgeH = 11;

        // Schwarzer Hintergrund für das Badge
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFF000000);
        // Antik-Goldener Rahmen
        int badgeBorder = 0xFFC0A060;
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, badgeBorder);
        guiGraphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, badgeBorder);
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, badgeBorder);
        guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, badgeBorder);

        // Textfarbe: Grün bei erreichtem Limit, sonst Hellblau
        int textColor = (this.currentCount >= requiredCards) ? 0x55FF55 : 0x55FFFF;
        guiGraphics.drawString(font, stackText, badgeX + 3, badgeY + 2, textColor, false);

        currentY += IMAGE_SIZE + 6;

        // --- 7. IMMERSIVER FLAVOR-TEXT (Unter dem Bild, zentriert umbrechend) ---
        Component flavorComp = getFlavorTextComponent();
        List<FormattedCharSequence> descLines = wrapText(font, flavorComp, CARD_WIDTH - 12);
        for (FormattedCharSequence line : descLines) {
            int lineWidth = font.width(line);
            int lineX = x + (CARD_WIDTH - lineWidth) / 2;
            // Rendert den Flavor-Text in einem leicht warmen Antik-Goldton/Lore-Weiß
            guiGraphics.drawString(font, line, lineX, currentY, 0xFFD3C294, true);
            currentY += 9;
        }
    }

    // --- UTILITY-METHODEN ---

    private Component getTitleComponent() {
        Skill skill = SkillManager.getSkill(this.skillRegistryName);
        if (skill != null) {
            return Component.translatable(skill.getTranslationKey());
        }
        ResourceLocation id = new ResourceLocation(this.skillRegistryName);
        return Component.literal(capitalize(id.getPath().replace("_", " ")));
    }

    /**
     * Ermittelt den passenden Lore-Text für diese Karte.
     * Prüft, ob ein spezifischer Eintrag existiert. Wenn nicht, wird deterministisch
     * einer von 10 generischen Stones-EF-Lore-Einträgen zugewiesen.
     */
	private Component getFlavorTextComponent() {
        Skill skill = SkillManager.getSkill(this.skillRegistryName);
        ResourceLocation id;
        
        // Sicherer Weg: Die echte, registrierte Location aus dem Skill holen!
        if (skill != null && skill.getRegistryName() != null) {
            id = skill.getRegistryName();
        } else {
            // Nur als absoluter Notfall-Fallback
            id = new ResourceLocation(this.skillRegistryName);
        }
        
        // Spezifischer Translation-Key, z.B.: flavor.stones_ef_bridge.epicfight.roll.text
        String specificKey = "flavor.stones_ef_bridge." + id.getNamespace() + "." + id.getPath() + ".text";

        if (I18n.exists(specificKey)) {
            return Component.translatable(specificKey);
        }

        // Generischer Key (1 - 10) basierend auf dem Modulo-Hash des Registrierungsnamens
        int hash = Math.abs(this.skillRegistryName.hashCode());
        int genericIndex = (hash % 10) + 1;
        String genericKey = "flavor.stones_ef_bridge.generic." + genericIndex;

        return Component.translatable(genericKey);
    }

    private List<FormattedCharSequence> wrapText(Font font, Component component, int width) {
        return font.split(component, width);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1).toLowerCase(Locale.ROOT);
    }
}