package net.stones_ef_bridge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.stones_ef_bridge.StonesEfBridge;
import net.stones_ef_bridge.util.SkillRarityManager;

/**
 * Rendert einen rotierenden, langsam pulsierenden und leicht flackernden Nimbus
 * (Heiligenschein-Ring) um das Item-Icon, sobald der Skillbuch-Stack die für
 * das Ritual benötigte Kartenanzahl erreicht hat.
 *
 * Render-Reihenfolge: IItemDecorator#render wird von Forge nach dem Item-Icon und
 * der Stackzahl aufgerufen. Die Nimbus-PNG hat in der Mitte (über dem 16x16
 * Item-Icon) einen komplett transparenten Bereich und einen hellen Ring auf
 * mittlerem Radius - das Icon bleibt sichtbar, der Heiligenschein legt sich
 * nur außen darum.
 *
 * DREI ÜBERLAGERTE ANIMATIONS-SCHICHTEN, alle rein zeitbasiert (kein State,
 * kein Random) und daher robust gegenüber Server-Resyncs, Stack-Splits etc.:
 *
 *   1. ROTATION   - konstant, linear, 360°/ROTATION_PERIOD_MS. Macht den Ring
 *                    "lebendig", ohne das Item-Icon selbst zu bewegen.
 *   2. PULSE       - eine einzelne, sehr langsame Sinuswelle auf Skalierung
 *                    und Alpha. Das "Atmen" des Heiligenscheins.
 *   3. JITTER      - Summe aus DREI Sinuswellen mit bewusst unrunden,
 *                    asynchronen Periodenlängen (730ms/1170ms/1910ms statt
 *                    z.B. 1000/2000/3000ms). Eine einzelne Sinuswelle oder
 *                    echter Zufall (Math.random()) würden hier falsch wirken:
 *                    eine einzelne Welle liest sich wie ein zweiter, schnellerer
 *                    Pulse (redundant zu Schicht 2); echter Zufall erzeugt
 *                    abrupte Sprünge, die wie Bildrauschen/Störung aussehen.
 *                    Die Summe dreier asynchroner, glatter Wellen ergibt
 *                    stattdessen ein organisches, nie exakt wiederholendes
 *                    Flackern (gleiches Prinzip wie Vanilla-Fackel-Flicker).
 *                    Amplitude ist bewusst klein gehalten, damit es als
 *                    Textur-Eigenschaft wirkt, nicht als technischer Fehler.
 *
 * Farbe ist an die Rarity des jeweiligen Skills gekoppelt (Common/Uncommon/
 * Rare/Legendary). Die PNG selbst muss dafür WEISS/GRAU + Alpha sein (kein
 * eingebackenes Farbpigment), da die Farbe zur Laufzeit per Shader-Tint
 * aufgebracht wird.
 */
public class StackCompleteDecorator implements IItemDecorator {

    // Pfad zur Nimbus-Textur (Heiligenschein-Ring). Erwartete Maße: 32x32,
    // Mitte transparent, heller Ring auf mittlerem Radius mit Falloff nach
    // innen UND außen, Inhalt weiß/grau (Farbe kommt per Tint zur Laufzeit).
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(StonesEfBridge.MODID, "textures/gui/item_glow.png");

    private static final int GLOW_SIZE = 32;
    private static final float GLOW_HALF = GLOW_SIZE / 2.0f;
    private static final float ITEM_CENTER = 8.0f; // Zentrum des 16x16 Item-Icons

    // --- Schicht 1: Rotation ---
    // Volle Umdrehung pro ROTATION_PERIOD_MS. Bewusst träge gewählt - ein
    // Heiligenschein soll schweben, nicht wie ein Mühlrad rotieren.
    private static final long ROTATION_PERIOD_MS = 8000L;

    // --- Schicht 2: Pulse (ganz langsames Atmen) ---
    private static final long PULSE_PERIOD_MS = 6000L;
    private static final float PULSE_ALPHA_MIN = 0.75f;
    private static final float PULSE_ALPHA_MAX = 1.0f;
    private static final float PULSE_SCALE_MIN = 0.96f;
    private static final float PULSE_SCALE_MAX = 1.04f;

    // --- Schicht 3: Jitter (organisches Flackern, keine Bildstörung) ---
    // Drei bewusst unrunde, zueinander asynchrone Periodenlängen.
    private static final long JITTER_PERIOD_A_MS = 730L;
    private static final long JITTER_PERIOD_B_MS = 1170L;
    private static final long JITTER_PERIOD_C_MS = 1910L;

    // Klein gehaltene Amplituden - Jitter soll als Textur-Detail wirken,
    // nicht als auffällige zweite Animation.
    private static final float JITTER_ALPHA_AMOUNT = 0.06f;
    private static final float JITTER_SCALE_AMOUNT = 0.015f;

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack,
                           int xOffset, int yOffset) {

        if (stack.getTag() == null || !stack.getTag().contains("skill")) {
            return false;
        }

        String skillId = stack.getTag().getString("skill");
        int required = SkillRarityManager.getCardCost(skillId);

        if (stack.getCount() < required) {
            return false;
        }

        SkillRarityManager.RarityInfo rarity = SkillRarityManager.getRarityInfo(required);
        long time = System.currentTimeMillis();

        // Schicht 1: Rotation
        float rotationPhase = (time % ROTATION_PERIOD_MS) / (float) ROTATION_PERIOD_MS;
        float angleDegrees = rotationPhase * 360.0f;

        // Schicht 2: Pulse (-1..1)
        float pulsePhase = (time % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
        float pulseWave = (float) Math.sin(pulsePhase * Math.PI * 2);
        float pulseNorm = 0.5f + 0.5f * pulseWave; // 0..1

        // Schicht 3: Jitter - Summe dreier asynchroner Sinuswellen, normiert auf -1..1
        float jitterWave = jitterNoise(time);

        // Alpha aus Pulse + Jitter kombinieren, am Ende auf [0,1] clampen
        float alpha = PULSE_ALPHA_MIN + pulseNorm * (PULSE_ALPHA_MAX - PULSE_ALPHA_MIN);
        alpha += jitterWave * JITTER_ALPHA_AMOUNT;
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        // Skalierung aus Pulse + Jitter kombinieren
        float scale = PULSE_SCALE_MIN + pulseNorm * (PULSE_SCALE_MAX - PULSE_SCALE_MIN);
        scale += jitterWave * JITTER_SCALE_AMOUNT;

        float r = ((rarity.color >> 16) & 0xFF) / 255f;
        float g = ((rarity.color >> 8) & 0xFF) / 255f;
        float b = (rarity.color & 0xFF) / 255f;

        drawNimbus(guiGraphics, xOffset, yOffset, angleDegrees, scale, r, g, b, alpha);

        return false; // false = Vanilla zeichnet Item-Icon und Stackzahl trotzdem normal
    }

    /**
     * Glattes, quasi-zufälliges Signal in [-1, 1] aus drei asynchronen Sinuswellen.
     * Bewusst KEIN Math.random() - das würde unstetige Sprünge erzeugen, die wie
     * eine Bildstörung statt wie organisches Flackern wirken. Die Summe dreier
     * Wellen mit unrunden, gegenseitig nicht teilbaren Periodenlängen wiederholt
     * sich erst nach sehr langer Zeit exakt und wirkt dadurch "lebendig".
     */
    private float jitterNoise(long time) {
        float phaseA = (time % JITTER_PERIOD_A_MS) / (float) JITTER_PERIOD_A_MS;
        float phaseB = (time % JITTER_PERIOD_B_MS) / (float) JITTER_PERIOD_B_MS;
        float phaseC = (time % JITTER_PERIOD_C_MS) / (float) JITTER_PERIOD_C_MS;

        float waveA = (float) Math.sin(phaseA * Math.PI * 2);
        float waveB = (float) Math.sin(phaseB * Math.PI * 2);
        float waveC = (float) Math.sin(phaseC * Math.PI * 2);

        // Summe von drei Wellen in [-1,1] liegt in [-3,3] -> auf [-1,1] normieren
        return (waveA + waveB + waveC) / 3.0f;
    }

    /**
     * Zeichnet die getintete Nimbus-Textur: rotiert und skaliert um ihr eigenes
     * Zentrum, zentriert über dem 16x16 Item-Icon. xOffset/yOffset entsprechen
     * der linken oberen Ecke des Icons im Slot.
     */
    private void drawNimbus(GuiGraphics guiGraphics, int xOffset, int yOffset,
                             float angleDegrees, float scale,
                             float r, float g, float b, float alpha) {

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // 1. Zum Item-Zentrum verschieben (Rotations-/Skalierungsachse muss hier liegen!)
        poseStack.translate(xOffset + ITEM_CENTER, yOffset + ITEM_CENTER, 0);

        // 2. Rotieren
        poseStack.mulPose(Axis.ZP.rotationDegrees(angleDegrees));

        // 3. Skalieren (wirkt um den aktuellen Ursprung, also um das Item-Zentrum -
        //    deshalb VOR der finalen Verschiebung zur Textur-Ecke anwenden)
        poseStack.scale(scale, scale, 1.0f);

        // 4. Zurück verschieben, sodass die Textur (Größe GLOW_SIZE) zentriert liegt
        poseStack.translate(-GLOW_HALF, -GLOW_HALF, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, alpha);

        guiGraphics.blit(
                GLOW_TEXTURE,
                0, 0,
                0, 0,
                GLOW_SIZE, GLOW_SIZE,
                GLOW_SIZE, GLOW_SIZE
        );

        // Shader-Farbe zwingend zurücksetzen, sonst "blutet" der Tint in
        // nachfolgende Render-Aufrufe (z.B. andere Slots, Tooltips) hinein!
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }
}