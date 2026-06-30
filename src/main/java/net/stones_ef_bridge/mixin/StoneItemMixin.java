package net.stones_ef_bridge.mixin;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.stones.item.StoneItem;
import net.stones_ef_bridge.events.StonesEfEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.item.SkillBookItem;

@Mixin(StoneItem.class)
public abstract class StoneItemMixin {
    //IMMER E:\stones_ef_bridge\build\createMcpToSrg\output.tsrg prüfen!!
    @Inject(
        method = "m_142305_",
        at = @At("HEAD"),
        remap = false, 
        cancellable = true
    )
    private void onOverrideOtherStackedOnMe(ItemStack stack, ItemStack cursorStack, Slot slot, ClickAction action, Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir) {
        if (action == ClickAction.SECONDARY && !cursorStack.isEmpty()) {
            // Unterstützt dank instanceof-Check auch alle Addons, die von SkillBookItem erben!
            if (cursorStack.getItem() instanceof SkillBookItem) {
				StoneItem stone = (StoneItem) stack.getItem();

				if (stone.getType() == StoneItem.Type.MILESTONE) {
					boolean result = StonesEfEventHandler.handleInventoryCrafting(player, stack, cursorStack, slot, access);
					if (result) {
					cir.setReturnValue(true);
					}
				}
			}
        }
    }
}