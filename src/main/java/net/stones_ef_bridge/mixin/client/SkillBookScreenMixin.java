package net.stones_ef_bridge.mixin.client;
// in .mixin so registriert:
//"client": [
//"client.SkillBookScreenMixin"
//]
//was man sich vielleicht auch denken könnte wenn man sieht dass es bereits hier in .client liegt.
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.client.gui.screen.SkillBookScreen;
import yesman.epicfight.skill.Skill;
import net.stones_ef_bridge.util.SkillRarityManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Clientseitiges Mixin für den Epic Fight Skill Book Screen.
 * Ersetzt den permanenten "Lernen"-Button durch ein interaktives, animiertes
 * Crafting-Tutorial-Panel (Anwendung) und zeigt bei Hover den detaillierten 
 * Risiko- und Mechanik-Tooltip (Funktion) an.
 *
 * BEHOBEN: Die fehlerhafte Registry-Durchsuchungs-Schleife wurde durch einen
 * direkten ID-Lookup ersetzt, um das versehentliche Laden des Cluster-Juwels zu verhindern.
 */
@Mixin(SkillBookScreen.class)
public abstract class SkillBookScreenMixin extends Screen {

    protected SkillBookScreenMixin(Component title) {
        super(title);
    }

    @Shadow(remap = false)
    private Button learnButton;

    @Shadow(remap = false)
    protected Skill skill;

    @Shadow(remap = false)
    private double customScale;

    @Unique
    private ItemStack stones$cachedMilestoneRune = null;

    @Unique
    private ItemStack stones$cachedSkillBook = null;

    // Speichert die unberührte Minecraft-Fensterskalierung, BEVOR Epic Fight sie manipuliert
    @Unique
    private double stones$originalScale = 0.0;

    // Pfad zur Nimbus-Glow Textur der Bridge für den Animations-Erfolg
    @Unique
    private static final ResourceLocation STONES$GLOW_TEXTURE =
            new ResourceLocation("stones_ef_bridge", "textures/gui/item_glow.png");

    @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"), remap = false)
    private void onInitTail(CallbackInfo ci) {
        if (this.learnButton != null) {
            this.learnButton.visible = false;
            this.learnButton.active = false;
        }

        // PRÄZISE BEHEBUNG: Direktes Laden über ForgeRegistries anstatt einer Registry-Schleife.
        // Das verhindert, dass "stones:cluster_jewel_milestone" fälschlicherweise vor "stones:rune_milestone" geladen wird.
        if (stones$cachedMilestoneRune == null) {
            Item milestoneRuneItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("stones", "rune_milestone"));
            
            if (milestoneRuneItem != null && milestoneRuneItem != Items.AIR) {
                stones$cachedMilestoneRune = new ItemStack(milestoneRuneItem);
            } else {
                // Fallback zu einer Barriere, falls die ID nicht registriert sein sollte
                stones$cachedMilestoneRune = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "barrier")));
            }
        }
    }

    /**
     * Erfasst die originale Fensterskalierung am Anfang des Render-Schritts.
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFZ)V", at = @At("HEAD"), remap = false)
    private void onRenderHead(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, boolean asBackground, CallbackInfo ci) {
        this.stones$originalScale = Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFZ)V", at = @At(value = "INVOKE", target = "Lyesman/epicfight/skill/Skill;getTranslationKey()Ljava/lang/String;"), remap = false)
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, boolean asBackground, CallbackInfo ci) {
        if (this.skill != null && !asBackground) { 
            SkillRarityManager.RarityInfo rarity = SkillRarityManager.getRarity(this.skill, Minecraft.getInstance().player);
            
            // Generiere das dynamische Skillbuch-ItemStack für die visuelle Darstellung
            if (stones$cachedSkillBook == null) {
                Item skillBookItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("epicfight", "skillbook"));
                if (skillBookItem != null) {
                    stones$cachedSkillBook = new ItemStack(skillBookItem);
                    CompoundTag tag = stones$cachedSkillBook.getOrCreateTag();
                    tag.putString("skill", this.skill.getRegistryName().toString());
                }
            }

            // --- KOORDINATEN DES INTERAKTIVEN TUTORIALS ---
            int panelX = this.width / 2 + 45;
            int panelY = this.height / 2 + 82;
            int panelW = 84;
            int panelH = 38;

            int slotLeftX = panelX + 8;
            int slotLeftY = panelY + 11;
            int slotRightX = panelX + panelW - 24;
            int slotRightY = panelY + 11;

            // --- 1. ZEICHNE DAS HINTERGRUND-PANEL MIT RARITÄTS-BORDER ---
            int borderCol = rarity.color | 0xFF000000;
            guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA0B0B0C);
            guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + 1, borderCol); // Oben
            guiGraphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, borderCol); // Unten
            guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, borderCol); // Links
            guiGraphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, borderCol); // Rechts

            // --- 2. ZEICHNE DIE REZESSIVEN SLOT-HINTERGRÜNDE ---
            guiGraphics.fill(slotLeftX - 1, slotLeftY - 1, slotLeftX + 17, slotLeftY + 17, 0xFF373737);
            guiGraphics.fill(slotLeftX, slotLeftY, slotLeftX + 16, slotLeftY + 16, 0xFF101010);

            guiGraphics.fill(slotRightX - 1, slotRightY - 1, slotRightX + 17, slotRightY + 17, 0xFF373737);
            guiGraphics.fill(slotRightX, slotRightY, slotRightX + 16, slotRightY + 16, 0xFF101010);

            // --- 3. BERECHNE DIE ZEITBASIERTE ANIMATIONS-LOGIK (3.5 Sekunden Loop) ---
            long time = System.currentTimeMillis();
            long cycle = time % 3500L;

            int currentBookX = slotRightX;
            int currentBookY = slotRightY;
            float bookAlpha = 1.0f;
            boolean drawCursor = false;
            boolean rightClicked = false;
            float glowScale = 0.0f;
            float glowAlpha = 0.0f;
            boolean drawFloatingText = false;
            float floatYOffset = 0.0f;
            float floatAlpha = 0.0f;

            if (cycle < 1000L) {
                // Phase 1: Ziehen des Buchs zur Rune (0ms - 1000ms)
                float t = cycle / 1000.0f;
                float ease = t * t * (3.0f - 2.0f * t); // Sanfter Start/Stop
                currentBookX = (int) (slotRightX + (slotLeftX - slotRightX) * ease);
                currentBookY = slotLeftY;
                drawCursor = true;
            } else if (cycle < 1800L) {
                // Phase 2: Klick & Resonanz-Start (1000ms - 1800ms)
                currentBookX = slotLeftX;
                currentBookY = slotLeftY;
                drawCursor = true;
                rightClicked = (cycle >= 1200L && cycle < 1600L); // Klick-Zustand
                if (cycle >= 1400L) {
                    glowScale = (cycle - 1400L) / 400.0f;
                    glowAlpha = 1.0f - glowScale;
                }
            } else if (cycle < 2600L) {
                // Phase 3: Verbrauch-Glow & Aufsteigende Verbrauchs-Schrift (1800ms - 2600ms)
                currentBookX = slotLeftX;
                currentBookY = slotLeftY;
                float consumeT = Math.min(1.0f, (cycle - 1800L) / 400.0f);
                bookAlpha = 1.0f - consumeT; // Buch blendet sich aus
                
                float resT = (cycle - 1800L) / 800.0f;
                glowScale = 1.0f + resT * 1.5f;
                glowAlpha = (1.0f - resT) * 0.8f; // Expansiver Pulskreis

                drawFloatingText = true;
                floatYOffset = resT * 12.0f; // Text fliegt nach oben
                floatAlpha = Math.min(1.0f, (1.0f - resT) * 2.0f);
            } else {
                // Phase 4: Zurücksetzen / Einblenden (2600ms - 3500ms)
                currentBookX = slotRightX;
                currentBookY = slotRightY;
                bookAlpha = (cycle - 2600L) / 900.0f; // Blendet sich am Startplatz wieder ein
            }

            // --- 4. ZEICHNE DIE STATISCHE MEILENSTEIN-RUNE (Verwendet nun das korrekte Cached-Item) ---
            if (stones$cachedMilestoneRune != null) {
                guiGraphics.renderFakeItem(stones$cachedMilestoneRune, slotLeftX, slotLeftY);
            }

            // --- 5. ZEICHNE DAS ANIMIERTE SKILLBUCH ---
            if (stones$cachedSkillBook != null && bookAlpha > 0.02f) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, bookAlpha);
                guiGraphics.renderFakeItem(stones$cachedSkillBook, currentBookX, currentBookY);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // --- 6. ZEICHNE CHEVRON-PFEILE "<<<" IN DER MITTE (RICHTUNG RUNE) ---
            int arrowX = panelX + 34;
            int arrowY = panelY + 16;
            long arrowPulse = (time / 250L) % 3;
            for (int i = 0; i < 3; i++) {
                int col = (i == (2 - arrowPulse)) ? borderCol : 0xFF555555;
                int chX = arrowX + i * 4;
                guiGraphics.fill(chX + 2, arrowY, chX + 3, arrowY + 5, col);
                guiGraphics.fill(chX + 1, arrowY + 1, chX + 2, arrowY + 2, col);
                guiGraphics.fill(chX + 1, arrowY + 3, chX + 2, arrowY + 4, col);
                guiGraphics.fill(chX, arrowY + 2, chX + 1, arrowY + 3, col);
            }

            // --- 7. ZEICHNE MAGISCHEN RUNE-GLOW BEIM KLICK ---
            if (glowAlpha > 0.01f) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                float r = ((rarity.color >> 16) & 0xFF) / 255f;
                float g = ((rarity.color >> 8) & 0xFF) / 255f;
                float b = (rarity.color & 0xFF) / 255f;
                RenderSystem.setShaderColor(r, g, b, glowAlpha);
                
                int glowSize = (int) (18 * glowScale);
                int glowHalf = glowSize / 2;
                int centerRuneX = slotLeftX + 8;
                int centerRuneY = slotLeftY + 8;
                
                guiGraphics.blit(
                        STONES$GLOW_TEXTURE,
                        centerRuneX - glowHalf, centerRuneY - glowHalf,
                        0, 0,
                        glowSize, glowSize,
                        glowSize, glowSize
                );
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // --- 8. ZEICHNE AUFSTEIGENDEN TEXT "-COST" ---
            if (drawFloatingText && floatAlpha > 0.01f) {
                String reqText = "-" + rarity.requiredCards;
                int textW = this.font.width(reqText);
                int textX = slotLeftX + 8 - (textW / 2);
                int textY = (int) (slotLeftY - 4 - floatYOffset);
                
                int alphaHex = ((int) (floatAlpha * 255.0f)) & 0xFF;
                int colorText = (alphaHex << 24) | 0xFF5555;
                int colorShadow = (alphaHex << 24) | 0x3F0000;
                
                guiGraphics.drawString(this.font, reqText, textX + 1, textY + 1, colorShadow, false);
                guiGraphics.drawString(this.font, reqText, textX, textY, colorText, false);
            }

            // --- 9. ZEICHNE DEN PIXEL-MAUSZEIGER ---
            if (drawCursor) {
                int cursorX = currentBookX + 10;
                int cursorY = currentBookY + 10;
                stones$drawMouseCursor(guiGraphics, cursorX, cursorY, rightClicked);
            }

            // --- 10. ZEICHNE DIE KOSTEN UND SELTENHEIT UNTER DAS PANEL ---
            int centerX = panelX + (panelW / 2);
            Component costText = Component.translatable("tooltip.stones_ef_bridge.required_books", rarity.requiredCards);
            guiGraphics.drawCenteredString(this.font, costText, centerX, panelY + panelH + 3, rarity.color);

            // --- 11. HOVER-ERKENNUNG MIT KORRIGIERTEN MAUS-KOORDINATEN ---
            double origScale = this.stones$originalScale != 0.0 ? this.stones$originalScale : Minecraft.getInstance().getWindow().getGuiScale();
            int scaledMouseX = (int) (mouseX * origScale / this.customScale);
            int scaledMouseY = (int) (mouseY * origScale / this.customScale);

            if (scaledMouseX >= panelX && scaledMouseX <= panelX + panelW && scaledMouseY >= panelY && scaledMouseY <= panelY + panelH + 15) {
                List<Component> tooltip = new ArrayList<>();
                
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_title").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                tooltip.add(Component.empty());
                
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_ritual1").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_ritual2", rarity.requiredCards).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_warning1").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_warning2").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_warning3").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());

                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_severity1", rarity.requiredCards).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_severity2", 
                    String.format(Locale.ROOT, "%.1f", rarity.requiredCards * 1.5), 
                    String.format(Locale.ROOT, "%.1f", rarity.requiredCards * 5.0)
                ).withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_severity3").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.empty());
                
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_consequence1").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_consequence2").withStyle(ChatFormatting.DARK_AQUA));
                tooltip.add(Component.translatable("gui.stones_ef_bridge.bloat_desc_consequence3").withStyle(ChatFormatting.DARK_AQUA));
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), scaledMouseX, scaledMouseY);
            }
        }
    }

    /**
     * Zeichnet einen kleinen, sauberen Pixel-Art Mauszeiger direkt über die GUI,
     * um die Rechtsklick-Aktion intuitiv zu demonstrieren.
     */
    @Unique
    private void stones$drawMouseCursor(GuiGraphics guiGraphics, int x, int y, boolean rightClick) {
        int colorBorder = 0xFF000000;
        int colorBg = 0xFFDDDDDD;
        int colorHighlight = 0xFFFFD700;
        
        guiGraphics.fill(x, y, x + 10, y + 14, colorBorder);
        guiGraphics.fill(x + 1, y + 1, x + 9, y + 13, colorBg);
        
        if (rightClick) {
            guiGraphics.fill(x + 5, y + 1, x + 9, y + 6, colorHighlight);
        }
        
        guiGraphics.fill(x + 1, y + 6, x + 9, y + 7, colorBorder);
        guiGraphics.fill(x + 5, y + 1, x + 6, y + 6, colorBorder);
    }
}