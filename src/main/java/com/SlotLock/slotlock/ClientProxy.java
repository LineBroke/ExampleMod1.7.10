package com.slotlock.slotlock;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    public static KeyBinding lockKey;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MyMod.LOG.info("SlotLock ClientProxy preInit called");

        super.preInit(event);

        /*
         * Register key binding.
         * Default key: Left Ctrl
         */
        lockKey = new KeyBinding("key.slotlock.toggle", Keyboard.KEY_LCONTROL, "key.categories.slotlock");

        ClientRegistry.registerKeyBinding(lockKey);

        /*
         * Overlay render handler.
         */
        SlotLockOverlayHandler overlayHandler = new SlotLockOverlayHandler();

        MinecraftForge.EVENT_BUS.register(overlayHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(overlayHandler);

        /*
         * Auto mover.
         */
        FMLCommonHandler.instance()
            .bus()
            .register(new SlotLockAutoMover());

        /*
         * Debug tool.
         * F9 = dump current container
         * F10 = toggle watch mode
         */
        FMLCommonHandler.instance()
            .bus()
            .register(SlotLockDebugClientHandler.instance());

        MyMod.LOG.info("SlotLock overlay registered");
        MyMod.LOG.info("SlotLock client tick handler registered");
        MyMod.LOG.info("SlotLock debug handler registered");
    }
}
