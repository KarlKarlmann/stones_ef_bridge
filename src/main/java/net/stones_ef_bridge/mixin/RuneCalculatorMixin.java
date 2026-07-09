package net.stones_ef_bridge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.stones.logic.RuneCalculator;
import net.stones.logic.RuneCalculator.CachedMilestone;
import net.stones_ef_bridge.events.StonesEfEventHandler;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
            if (cached.rune instanceof StonesEfEventHandler.EpicFightRune efRune) {
                ResourceLocation originalId = efRune.getOriginalSkillId();
                Skill skill = SkillManager.getSkill(originalId.toString());
                
                if (skill != null) {
                    authorizedSkillObjects.add(skill);
                }
            }
        }

        // 2. Prüfen, ob sich die ausgerüsteten Skills vom Schrein unterscheiden
        boolean needsClear = false;  // TRUE, wenn wir Skills entfernen müssen (Swipe nötig)
        boolean needsAppend = false; // TRUE, wenn wir Skills nur hinzufügen müssen
        
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
                    
                    // FALL 1: Hat der Spieler einen Skill ausgerüstet, den der Schrein nicht (mehr) hergibt?
                    if (!authorizedSkillObjects.contains(equippedSkill)) {
                        needsClear = true; // Skill muss weg -> SWIPE ZWINGEND NÖTIG!
                    }
                }
            }
        }

        // FALL 2: Fehlt ein Skill in der Ausrüstung, der im Schrein liegt?
        Set<Skill> missingSkills = new HashSet<>();
        for (Skill authorized : authorizedSkillObjects) {
            if (!currentlyEquipped.contains(authorized)) {
                missingSkills.add(authorized);
                needsAppend = true; // Neuer Skill -> HINZUFÜGEN NÖTIG!
            }
        }

        /* * =========================================================================
         * ARCHITEKTUR-HINWEIS: WARUM "SWIPE & REBUILD"?
         * =========================================================================
         * In "Stones" werden Epic Fight Skills durch Runen in einem Schrein freigeschaltet.
         * Da sich XP-Level in Minecraft dynamisch ändern können, müssen Skills oft 
         * on-the-fly entzogen werden.
         * Ein einzelnes "Entlernen" von Skills direkt über die Epic Fight API führt
         * in der Praxis zu massivem Chaos und Problemen bei der Client-Server-Synchronisation.
         * Der robusteste Weg zum ENTFERNEN von Skills ist ein vollständiger Reset (Swipe) 
         * mit anschließendem Neuaufbau (Rebuild). Um Cooldown-Strafen zu vermeiden, 
         * speichern wir zuvor das Slot-Layout ab und stellen es exakt so wieder her.
         * =========================================================================
         */

        // 3. AKTION AUSFÜHREN BASIEREND AUF DEM ZUSTAND
        if (needsClear) {
            // ========================================================
            // HARD RESET (SWIPE & REBUILD) - Nur wenn Skills gelöscht werden müssen
            // ========================================================
            
            // A) VOR DEM RESET: Altes Layout (Slot -> Skill) merken
            Map<String, Skill> previousLayout = new HashMap<>();
            Set<Skill> nativeSkillsToRestore = new HashSet<>(); // +++ WICHTIG: Backup für Basic Attacks +++

            if (skillCap.skillContainers != null) {
                for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                    if (container.getSlot() != null && container.getSkill() != null) {
                        Skill skill = container.getSkill();
                        previousLayout.put(container.getSlot().toString(), skill);
                        
                        // Sichern der nativen Skills vor dem Wipe
                        String catName = skill.getCategory() != null ? skill.getCategory().toString() : "";
                        if ("WEAPON_PASSIVE".equals(catName) || "BASIC_ATTACK".equals(catName)) {
                            nativeSkillsToRestore.add(skill);
                        }
                    }
                }
            }

            // B) OFFIZIELLER RESET (Löscht alles Nativ, INKLUSIVE Basis-Angriffe!)
            skillCap.clearContainersAndLearnedSkills(true);
            SPClearSkills clearpacket = new SPClearSkills(player.getId());
            EpicFightNetworkManager.sendToPlayer(clearpacket, player);
            EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(clearpacket, player);

            // C) NEU LERNEN
            // Erstelle eine Liste aus Schrein-Skills + den geretteten Basis-Angriffen
            Set<Skill> allSkillsToLearnAndEquip = new HashSet<>(authorizedSkillObjects);
            allSkillsToLearnAndEquip.addAll(nativeSkillsToRestore);

            for (Skill skill : allSkillsToLearnAndEquip) {
                try {
                    skillCap.addLearnedSkill(skill);
                } catch (Exception ignored) {}
            }

            // D) AUSRÜSTEN - ALTE POSITIONEN PRIORISIEREN
            if (skillCap.skillContainers != null) {
                Set<Skill> remainingSkills = new HashSet<>(allSkillsToLearnAndEquip);

                // Schritt 1: Exakt die alten Positionen wiederherstellen
                for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                    if (container.getSlot() != null) {
                        String slotName = container.getSlot().toString();
                        Skill oldSkill = previousLayout.get(slotName);
                        
                        if (oldSkill != null && remainingSkills.contains(oldSkill)) {
                            container.setSkill(oldSkill);
                            // +++ FIX: Zwingend den Cooldown nach dem Setzen auf 0 nullen! +++
                            // (Abhängig von deiner EF-Version, evtl. über Reflection wenn die Methode private ist)
                            try { container.setReplaceCooldown(0); } catch (Exception ignored) {}
                            
                            remainingSkills.remove(oldSkill); 
                        }
                    }
                }

                // Schritt 2: Übrig gebliebene auf freie Slots verteilen
                for (Skill skill : remainingSkills) {
                    yesman.epicfight.skill.SkillContainer firstEmptyMatchingSlot = null;
                    for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                        if (container.getSlot() != null && skill.getCategory() != null) {
                            String slotName = container.getSlot().toString();
                            String catName = skill.getCategory().toString();
                            boolean matches = slotName.equalsIgnoreCase(catName) || (catName.equalsIgnoreCase("PASSIVE") && slotName.toUpperCase().startsWith("PASSIVE"));
                            if (matches && container.getSkill() == null && firstEmptyMatchingSlot == null) {
                                firstEmptyMatchingSlot = container;
                            }
                        }
                    }
                    if (firstEmptyMatchingSlot != null) {
                        firstEmptyMatchingSlot.setSkill(skill);
                        // +++ FIX: Zwingend den Cooldown nach dem Setzen auf 0 nullen! +++
                        try { firstEmptyMatchingSlot.setReplaceCooldown(0); } catch (Exception ignored) {}
                    }
                }
            }

            // E) NEUE KONFIGURATION AN DEN CLIENT SENDEN
            EpicFightNetworkManager.sendToPlayer(new SPInitSkills(skillCap), player);

        } else if (needsAppend) {
            // ========================================================
            // SOFT APPEND - Kein Swipe! Es kommen nur neue Skills dazu.
            // ========================================================
            
            for (Skill missingSkill : missingSkills) {
                // Skill beibringen
                try {
                    skillCap.addLearnedSkill(missingSkill);
                } catch (Exception ignored) {}

                // In den ersten freien, passenden Slot legen
                if (skillCap.skillContainers != null) {
                    yesman.epicfight.skill.SkillContainer firstEmptyMatchingSlot = null;
                    for (yesman.epicfight.skill.SkillContainer container : skillCap.skillContainers) {
                        if (container.getSlot() != null && missingSkill.getCategory() != null) {
                            String slotName = container.getSlot().toString();
                            String catName = missingSkill.getCategory().toString();
                            
                            boolean matches = slotName.equalsIgnoreCase(catName) || (catName.equalsIgnoreCase("PASSIVE") && slotName.toUpperCase().startsWith("PASSIVE"));
                            
                            if (matches && container.getSkill() == null && firstEmptyMatchingSlot == null) {
                                firstEmptyMatchingSlot = container;
                            }
                        }
                    }

                    if (firstEmptyMatchingSlot != null) {
                        firstEmptyMatchingSlot.setSkill(missingSkill);
                        // +++ FIX: Zwingend den Cooldown nach dem Setzen auf 0 nullen! +++
                        try { firstEmptyMatchingSlot.setReplaceCooldown(0); } catch (Exception ignored) {}
                    }
                }
            }
            
            // Aktualisierung an den Client senden, ohne dass die anderen Skills resettet wurden
            EpicFightNetworkManager.sendToPlayer(new SPInitSkills(skillCap), player);
        }
    }
}