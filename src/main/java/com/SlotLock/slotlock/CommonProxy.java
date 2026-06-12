package com.slotlock.slotlock;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        MyMod.LOG.info("SlotLock CommonProxy preInit called");

        SlotLockManager.setSaveFile(event.getModConfigurationDirectory());

        MyMod.LOG.info("SlotLock loaded");
    }

    public void init(FMLInitializationEvent event) {
        MyMod.LOG.info("SlotLock CommonProxy init called");
    }

    public void postInit(FMLPostInitializationEvent event) {
        MyMod.LOG.info("SlotLock CommonProxy postInit called");
    }

    public void serverStarting(FMLServerStartingEvent event) {}
}
