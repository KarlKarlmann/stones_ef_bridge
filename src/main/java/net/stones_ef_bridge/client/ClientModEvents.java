package net.stones_ef_bridge.client;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones_ef_bridge.StonesEfBridge;
import net.stones_ef_bridge.client.tooltip.ClientSkillTooltip;
import net.stones_ef_bridge.util.SkillRarityManager;
import yesman.epicfight.world.item.SkillBookItem;

/**
 * Clientseitige Setup-Klasse für die Stones Epic Fight Bridge.
 * Registriert dynamische Modell-Overrides und fängt die Tooltips der Skillbücher ab.
 */
@Mod.EventBusSubscriber(modid = StonesEfBridge.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientModEvents {

    // Diese Variable wird automatisch vom generate_models.py gepatcht.
    public static final int totalVariants = 1;

    /**
     * Setzt den clientseitigen Cache zurück, wenn die Verbindung zum Server getrennt wird.
     */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SkillRarityManager.clearSynchronizedConfigs();
    }

    /**
     * Fängt alle Tooltip-Zeilen ab, unterdrückt die originalen Epic Fight Texte vollständig
     * und ersetzt sie exklusiv durch unsere immersive Divination Card mit dynamischem Flavor-Text.
     */
	@SubscribeEvent
		public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
			ItemStack stack = event.getItemStack();
			
			if (stack.getItem() instanceof SkillBookItem) {
				// Epic Fight API nutzen, anstatt NBT manuell zu prüfen!
				yesman.epicfight.skill.Skill skill = SkillBookItem.getContainSkill(stack);
				
				if (skill != null && skill.getRegistryName() != null) {
					// Das gibt uns IMMER den echten Namespace, z.B. "epicfight:roll" oder "addon:skill"
					String safeSkillId = skill.getRegistryName().toString();
					
					var elements = event.getTooltipElements();

					// 1. Alle originalen Unterzeilen verwerfen (Index 0 ist der Name des Buchs, den behalten wir!)
					if (elements.size() > 1) {
						elements.subList(1, elements.size()).clear();
					}

					// 2. Unsere Divination Card mit dem sicheren String initialisieren
					elements.add(1, Either.right(new ClientSkillTooltip(safeSkillId, stack)));
				}
			}
		}

    /**
     * Registrierung auf dem MOD-Bus.
     */
    @Mod.EventBusSubscriber(modid = StonesEfBridge.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			event.enqueueWork(() -> {
				Item skillBook = ForgeRegistries.ITEMS.getValue(new ResourceLocation("epicfight", "skillbook"));
				if (skillBook != null) {
					ItemProperties.register(skillBook, new ResourceLocation(StonesEfBridge.MODID, "variant"), (stack, level, entity, seed) -> {
						
						if (stack.getItem() instanceof SkillBookItem) {
							yesman.epicfight.skill.Skill skill = SkillBookItem.getContainSkill(stack);
							
							if (skill != null && skill.getRegistryName() != null) {
								// Sicheren String für den konstanten Hash nutzen
								String safeSkillId = skill.getRegistryName().toString();
								int hash = Math.abs(safeSkillId.hashCode());
								return (float) (hash % totalVariants);
							}
						}
						return 0.0F;
					});
				}
			});
		}

        @SubscribeEvent
        public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
            Item skillBook = ForgeRegistries.ITEMS.getValue(new ResourceLocation("epicfight", "skillbook"));
            if (skillBook != null) {
                event.register(skillBook, new StackCompleteDecorator());
            }
        }

		@SubscribeEvent
        public static void onRegisterTooltipComponent(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ClientSkillTooltip.class, component -> component);
        }
    }
}