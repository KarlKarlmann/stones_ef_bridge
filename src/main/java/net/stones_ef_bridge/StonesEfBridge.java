package net.stones_ef_bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.stones_ef_bridge.events.StonesEfEventHandler;
import net.stones_ef_bridge.network.StonesNetwork;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StonesEfBridge.MODID)
public class StonesEfBridge {
    public static final String MODID = "stones_ef_bridge";
    public static final Logger LOGGER = LogManager.getLogger();

    public StonesEfBridge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Registrieren des Netzwerk-Kanals
        StonesNetwork.register();

        // Event-Listener für Config-Reloads registrieren, um den Cache dynamisch zu leeren!
        modEventBus.addListener(this::onConfigReload);

        // Registrierung der Event-Handler Klasse auf dem Forge-Bus
        MinecraftForge.EVENT_BUS.register(new StonesEfEventHandler());
        
        LOGGER.info("Stones-Epic Fight Bridge erfolgreich mit dynamischer Config geladen!");
    }

    private void onConfigReload(final net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getModId().equals(MODID)) {
            LOGGER.info("[Stones-EF-Bridge] Konfiguration wurde neu geladen! Cache wird aktualisiert...");
            net.stones_ef_bridge.util.SkillRarityManager.markCacheDirty();
        }
    }
}