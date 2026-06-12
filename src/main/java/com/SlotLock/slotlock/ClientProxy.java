package com.SlotLock.slotlock;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MyMod.LOG.info("SlotLock ClientProxy preInit called");

        super.preInit(event);

        SlotLockOverlayHandler handler = new SlotLockOverlayHandler();

        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);

        MyMod.LOG.info("SlotLock overlay registered");
        MyMod.LOG.info("SlotLock client tick handler registered");
    }
}
