package com.slotlock.slotlock;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    // 定义一个全局可见的 KeyBinding
    public static KeyBinding lockKey;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MyMod.LOG.info("SlotLock ClientProxy preInit called");

        super.preInit(event);

        // 注册按键：
        // 参数1: translation key (用于在语言文件里配置显示名称)
        // 参数2: 默认按键 (左 Ctrl)
        // 参数3: 分类名称 (在游戏控制菜单里单独列出一块)
        lockKey = new KeyBinding("key.slotlock.toggle", Keyboard.KEY_LCONTROL, "key.categories.slotlock");
        ClientRegistry.registerKeyBinding(lockKey);

        SlotLockOverlayHandler handler = new SlotLockOverlayHandler();

        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);

        MyMod.LOG.info("SlotLock overlay registered");
        MyMod.LOG.info("SlotLock client tick handler registered");
    }
}
