package net.stones_ef_bridge.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.stones.enchantment.RuneEnchantment;
import net.stones.logic.RuneCalculator;
import net.stones_ef_bridge.StonesEfBridge;
import net.stones_ef_bridge.compat.EpicSkillsCompat;
import net.stones_ef_bridge.util.SkillRarityManager;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import net.minecraftforge.fml.ModLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = StonesEfBridge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesEfEventHandler {

    @SubscribeEvent
	public static void onRegisterEnchantments(RegisterEvent event) {
		if (event.getRegistryKey().equals(ForgeRegistries.Keys.ENCHANTMENTS)) {
			StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Generiere dynamische Runen-Verzauberungen...");

            List<ResourceLocation> skillsToRegister = new ArrayList<>();

            // 1. Hole ALLE Skills, indem wir Epic Fights eigenes SkillBuildEvent VORZEITIG manuell feuern!
            try {
                SkillBuildEvent skillBuildEvent = new SkillBuildEvent();
                ModLoader.get().postEvent(skillBuildEvent);
                
                for (Skill skill : skillBuildEvent.getAllSkills()) {
                    if (skill.getRegistryName() != null) {
                        skillsToRegister.add(skill.getRegistryName());
                    }
                }
                StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] {} Skills erfolgreich durch SkillBuildEvent extrahiert!", skillsToRegister.size());
            } catch (Exception e) {
                StonesEfBridge.LOGGER.error("[Stones-EF-Bridge] Fehler beim Vorab-Laden der Epic Fight Skills", e);
            }

            // 2. Erweitere um alle Addon-Skills, die der Spieler in der Config hinterlegt hat!
            try {
                if (EpicSkillsCompat.CUSTOM_RARITIES != null && EpicSkillsCompat.CUSTOM_RARITIES.get() != null) {
                    for (String entry : EpicSkillsCompat.CUSTOM_RARITIES.get()) {
                        String[] parts = entry.split("=");
                        if (parts.length >= 1) {
                            skillsToRegister.add(new ResourceLocation(parts[0].trim()));
                        }
                    }
                }
            } catch (Exception e) {
                StonesEfBridge.LOGGER.error("[Stones-EF-Bridge] Fehler beim Lesen der Custom Rarities", e);
            }

            // Duplikate entfernen
            List<ResourceLocation> uniqueSkills = skillsToRegister.stream().distinct().collect(Collectors.toList());

			for (ResourceLocation skillId : uniqueSkills) {
                String registryName = skillId.getNamespace() + "_" + skillId.getPath();
				String displayName = "DICT:skill." + skillId.getNamespace() + "." + skillId.getPath();
				String desc = "DICT:skill." + skillId.getNamespace() + "." + skillId.getPath() + ".tooltip";

				int cardsRequired = SkillRarityManager.getCardCost(skillId.toString());
				float baseReq = cardsRequired <= 3 ? 1.0f : (cardsRequired <= 5 ? 2.0f : (cardsRequired <= 8 ? 3.0f : 4.0f));

				RuneEnchantment dynamicSkillRune = new RuneEnchantment(
					RuneEnchantment.Type.MILESTONE,
					(Attribute) null,
					null,
					0.0,
					displayName,
					desc,
					skillId.getNamespace() + ":textures/gui/skills/" + skillId.getPath() + ".png",
					baseReq, 
					false  
				) {
                    @Override
                    public void sleep() {
                        // IMMUNITÄT: Diese programmatische Epic Fight Rune blockiert den "sleep()" Befehl!
                        // Dadurch wird sie weder vom Server Datapack-Reload noch vom Client Sync-Paket ausradiert 
                        // und fällt niemals in den Status "Erloschene Resonanz" zurück.
                    }
                };

                dynamicSkillRune.setMaxLevel(10);
                event.register(ForgeRegistries.Keys.ENCHANTMENTS, new ResourceLocation(StonesEfBridge.MODID, registryName), () -> dynamicSkillRune);
            }
            
            StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Erfolgreich {} dynamische Runen-Verzauberungen registriert!", uniqueSkills.size());

            // 4. ZUSÄTZLICH: Frei definierbare Wildcard-Slots am Ende deklarieren
            try {
                StonesEfBridge.LOGGER.info("[Stones-EF-Bridge] Reserviere zusätzlich Wildcard-Slots für Epic-Fight Datapacks...");
                net.stones.init.StonesModEnchantments.registerModSlots(event, StonesEfBridge.MODID, 50, 50, 100);
            } catch (Exception e) {
                StonesEfBridge.LOGGER.error("[Stones-EF-Bridge] Fehler beim Zuweisen der Wildcard-Slots", e);
            }
        }
    }

    public static boolean handleInventoryCrafting(Player player, ItemStack runeStack, ItemStack bookStack, Slot slot, SlotAccess cursorAccess) {
        if (player.level().isClientSide) return true;

        if (bookStack.getTag() == null || !bookStack.getTag().contains("skill")) {
            return false;
        }

        String skillRegistryName = bookStack.getTag().getString("skill");
        ResourceLocation skillRes = new ResourceLocation(skillRegistryName);
        
        ResourceLocation targetEnchKey = new ResourceLocation(StonesEfBridge.MODID, skillRes.getNamespace() + "_" + skillRes.getPath());
        Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetEnchKey);

        if (targetEnchantment == null) {
            player.displayClientMessage(Component.translatable("message.stones_ef_bridge.incompatible_skill").withStyle(ChatFormatting.RED), true);
            player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        Map<Enchantment, Integer> currentEnchants = EnchantmentHelper.getEnchantments(runeStack);
        if (currentEnchants.containsKey(targetEnchantment)) {
            player.displayClientMessage(Component.translatable("message.stones_ef_bridge.already_known").withStyle(ChatFormatting.YELLOW), true);
            player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        int cardCost = SkillRarityManager.getCardCost(skillRegistryName);
        SkillRarityManager.RarityInfo rarity = SkillRarityManager.getRarityInfo(cardCost);
        int requiredStackSize = rarity.requiredCards;

        if (bookStack.getCount() < requiredStackSize && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.stones_ef_bridge.insufficient_cards", requiredStackSize).withStyle(ChatFormatting.RED), true);
            player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        boolean instabilityBloatOccurred = false;
        boolean massiveBloatOccurred = false;
        Map<Enchantment, Integer> newEnchants = new HashMap<>(currentEnchants);
        int n = currentEnchants.size();
        
        if (n > 0) {
            List<Enchantment> keys = new ArrayList<>(currentEnchants.keySet());
            Collections.shuffle(keys);
            
            for (int i = 0; i < n; i++) {
                double chance;
                if (n == 1) {
                    chance = 0.5;
                } else {
                    chance = 1.0 - ((double) i * 0.5 / (n - 1));
                }
                
                if (player.getRandom().nextDouble() < chance) {
                    Enchantment e = keys.get(i);
                    int currentLvl = newEnchants.get(e);
                    int maxLvl = e.getMaxLevel();
                    
                    if (currentLvl < maxLvl) {
                        double severityRoll = player.getRandom().nextDouble();
                        int levelsToAdd = 1;
                        
                        double chanceForPlus3 = requiredStackSize * 0.015; 
                        double chanceForPlus2 = requiredStackSize * 0.05;  
                        
                        if (severityRoll < chanceForPlus3) {
                            levelsToAdd = 3;
                            massiveBloatOccurred = true;
                        } else if (severityRoll < chanceForPlus2) {
                            levelsToAdd = 2;
                            massiveBloatOccurred = true;
                        }
                        
                        int finalLvl = Math.min(maxLvl, currentLvl + levelsToAdd);
                        newEnchants.put(e, finalLvl);
                        instabilityBloatOccurred = true;
                    }
                }
            }
        }

        newEnchants.put(targetEnchantment, 1);

        EnchantmentHelper.setEnchantments(newEnchants, runeStack);
        
        slot.setChanged();

        if (!player.getAbilities().instabuild) {
            bookStack.shrink(requiredStackSize);
            cursorAccess.set(bookStack.isEmpty() ? ItemStack.EMPTY : bookStack);
        }

        ServerLevel world = (ServerLevel) player.level();
        
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.3f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f);

        world.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        world.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.4, 0.4, 0.4, 0.08);

        if (massiveBloatOccurred) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.5f);
            world.sendParticles(ParticleTypes.DRAGON_BREATH, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.4, 0.4, 0.02);
            world.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        }

        Component skillNameComp = Component.translatable("epicfight.skill." + skillRes.getPath()).withStyle(ChatFormatting.GOLD);
        player.displayClientMessage(Component.translatable("message.stones_ef_bridge.rune_resonated", skillNameComp).withStyle(ChatFormatting.GREEN), true);

        if (massiveBloatOccurred) {
            player.displayClientMessage(Component.translatable("message.stones_ef_bridge.massive_bloat").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        } else if (instabilityBloatOccurred) {
            player.displayClientMessage(Component.translatable("message.stones_ef_bridge.instability_bloat").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            RuneCalculator.updatePlayer(serverPlayer);
        }

        return true;
    }
}