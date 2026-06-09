package com.SlotLock.slotlock;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        MinecraftForge.EVENT_BUS.register(new SlotLockOverlayHandler());
    }
}
