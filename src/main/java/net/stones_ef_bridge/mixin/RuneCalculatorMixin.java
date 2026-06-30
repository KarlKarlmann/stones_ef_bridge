package net.stones_ef_bridge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.logic.RuneCalculator;
import net.stones.logic.RuneCalculator.CachedMilestone;
import net.stones_ef_bridge.StonesEfBridge; // <-- NEU: Importiert die Bridge für MODID
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.data.reloader.SkillManager;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.network.EpicFightNetworkManager;
import yesman.epicfight.network.server.SPClearSkills;
import yesman.epicfight.network.server.SPInitSkills;

import java.util.HashSet;
import java.util.Set;

@Mixin(RuneCalculator.class)
public class RuneCalculatorMixin {

    @Inject(method = "updatePlayer", at = @At("RETURN"), remap = false)
    private static void onUpdatePlayerReturn(ServerPlayer player, CallbackInfo ci) {
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;

        var skillCap = patch.getSkillCapability();
        if (skillCap == null) return;

		// 1. Sammle ALLE Skills, die der Schrein aktuell erlaubt
		Set<Skill> authorizedSkillObjects = new HashSet<>();
		for (CachedMilestone cached : RuneCalculator.getActiveMilestones(player)) {
			if (cached.runeId != null && cached.runeId.getNamespace().equals(StonesEfBridge.MODID)) {
                // FIX: Rekonstruiert die Skill-ID dynamisch (z.B. "stones_ef_bridge:epicfight_roll" -> "epicfight:roll")
                String path = cached.runeId.getPath();
                int firstUnderscore = path.indexOf('_');
                if (firstUnderscore > 0) {
                    String skillNamespace = path.substring(0, firstUnderscore);
                    String skillPath = path.substring(firstUnderscore + 1);
                    Skill skill = SkillManager.getSkill(skillNamespace + ":" + skillPath);
                    if (skill != null) {
                        authorizedSkillObjects.add(skill);
                    }
                }
			}
		}

        // 2. Prüfen, ob sich die ausgerüsteten Skills vom Schrein unterscheiden
        boolean needsSync = false;
        Set<Skill> currentlyEquipped = new HashSet<>();
        
        if (skillCap.skillContainers != null) {
            for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                Skill equippedSkill = container.getSkill();
                if (equippedSkill != null) {
                    String catName = equippedSkill.getCategory() != null ? equippedSkill.getCategory().toString() : "";
                    
                    // Native Vanilla/EF Basis-Angriffe ignorieren
                    if ("WEAPON_PASSIVE".equals(catName) || "BASIC_ATTACK".equals(catName)) {
                        continue;
                    }
                    
                    currentlyEquipped.add(equippedSkill);
                    
                    // Hat der Spieler einen Skill ausgerüstet, den der Schrein nicht (mehr) hergibt?
                    if (!authorizedSkillObjects.contains(equippedSkill)) {
                        needsSync = true;
                    }
                }
            }
        }

        // Fehlt ein Skill in der Ausrüstung, der im Schrein liegt?
        if (!needsSync) {
            for (Skill authorized : authorizedSkillObjects) {
                if (!currentlyEquipped.contains(authorized)) {
                    needsSync = true;
                    break;
                }
            }
        }

        // 3. WENN SICH ETWAS GEÄNDERT HAT -> Nativer Epic Fight Reset & Neuaufbau!
        if (needsSync) {
            
            // A) OFFIZIELLER RESET (Löscht alles Nativ!)
            skillCap.clearContainersAndLearnedSkills(true);
            SPClearSkills clearpacket = new SPClearSkills(player.getId());
            EpicFightNetworkManager.sendToPlayer(clearpacket, player);
            EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(clearpacket, player);

            // B) NEU LERNEN UND AUSRÜSTEN
            for (Skill skill : authorizedSkillObjects) {
                
                try {
                    skillCap.addLearnedSkill(skill);
                } catch (Exception ignored) {}

                if (skillCap.skillContainers != null) {
                    yesman.epicfight.skill.SkillContainer firstEmptyMatchingSlot = null;

                    for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                        if (container.getSlot() != null && skill.getCategory() != null) {
                            String slotName = container.getSlot().toString();
                            String catName = skill.getCategory().toString();
                            
                            // Nutzt nun case-insensitive Vergleiche für absolute Zuverlässigkeit
                            boolean matches = slotName.equalsIgnoreCase(catName) || (catName.equalsIgnoreCase("PASSIVE") && slotName.toUpperCase().startsWith("PASSIVE"));
                            
                            if (matches && container.getSkill() == null && firstEmptyMatchingSlot == null) {
                                firstEmptyMatchingSlot = container;
                            }
                        }
                    }

                    if (firstEmptyMatchingSlot != null) {
                        firstEmptyMatchingSlot.setSkill(skill);
                    }
                }
            }

            // C) NEUE KONFIGURATION AN DEN CLIENT SENDEN
            EpicFightNetworkManager.sendToPlayer(new SPInitSkills(skillCap), player);
        }
    }
}