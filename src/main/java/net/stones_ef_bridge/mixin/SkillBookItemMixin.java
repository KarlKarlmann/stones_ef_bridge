package net.stones_ef_bridge.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.stones_ef_bridge.util.SkillRarityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.world.item.SkillBookItem;

import java.util.List;

@Mixin(SkillBookItem.class)
public class SkillBookItemMixin {

    /**
     * ELEGANTER WEG FÜR DIE STACKGRÖSSE:
     * Fängt das Item.Properties-Objekt ab, BEVOR es an den super() Konstruktor 
     * der Basis-Klasse übergeben wird. Wir überschreiben das .stacksTo(1) 
     * einfach hart mit .stacksTo(64).
     */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;<init>(Lnet/minecraft/world/item/Item$Properties;)V"))
    private static Item.Properties modifyProperties(Item.Properties properties) {
        return properties.stacksTo(64);
    }
}