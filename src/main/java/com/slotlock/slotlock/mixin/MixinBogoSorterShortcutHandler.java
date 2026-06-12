package com.slotlock.slotlock.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.bogosorter.BogoSortAPI;
import com.cleanroommc.bogosorter.ClientEventHandler;
import com.cleanroommc.bogosorter.ShortcutHandler;
import com.cleanroommc.bogosorter.common.sort.GuiSortingContext;
import com.cleanroommc.bogosorter.common.sort.SlotGroup;
import com.cleanroommc.bogosorter.mixins.early.minecraft.SlotAccessor;
import com.slotlock.slotlock.SlotLockBogoShortcutGuard;
import com.slotlock.slotlock.SlotLockManager;

@Mixin(value = ShortcutHandler.class, remap = false)
public abstract class MixinBogoSorterShortcutHandler {

    @Unique
    private static long slotlock$lastShortcutTime = 0L;

    @Unique
    private static final long slotlock$shortcutCooldownMs = 80L;

    @Unique
    private static final long slotlock$vanillaClickGuardMs = 300L;

    @Unique
    private static boolean slotlock$shouldBlockRepeatedShortcut() {
        if (!SlotLockManager.hasAnyLock()) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - slotlock$lastShortcutTime < slotlock$shortcutCooldownMs) {
            return true;
        }

        slotlock$lastShortcutTime = now;
        return false;
    }

    /**
     * 客户端入口：
     * Alt + 左键移动整组物品。
     *
     * 重点：
     * 1. 冷却中返回 true，吞掉这次点击。
     * 2. 没有合法目标时返回 true，吞掉这次点击。
     * 3. 允许 BogoSorter 原逻辑继续时，也临时阻止原版左键 fallback。
     */
    @Inject(
        method = "moveAllItems(Lnet/minecraft/client/gui/inventory/GuiContainer;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$cooldownClientMoveAll(GuiContainer guiContainer, boolean sameItemOnly,
        CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (slotlock$shouldBlockRepeatedShortcut()) {
            SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        if (!slotlock$clientMoveAllHasValidTarget(guiContainer, sameItemOnly)) {
            SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        /*
         * 这次允许 BogoSorter 原逻辑继续发 shortcut 包。
         * 但也要临时屏蔽原版左键 fallback。
         */
        SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
    }

    /**
     * 客户端入口：
     * Alt + 其他快捷移动，移动单个物品。
     */
    @Inject(
        method = "moveSingleItem(Lnet/minecraft/client/gui/inventory/GuiContainer;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$cooldownClientMoveSingle(GuiContainer guiContainer, boolean emptySlot,
        CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (slotlock$shouldBlockRepeatedShortcut()) {
            SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
    }

    /**
     * 客户端入口：
     * 快捷丢弃物品。
     */
    @Inject(
        method = "dropItems(Lnet/minecraft/client/gui/inventory/GuiContainer;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$cooldownClientDrop(GuiContainer guiContainer, boolean onlySameType,
        CallbackInfoReturnable<Boolean> cir) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (slotlock$shouldBlockRepeatedShortcut()) {
            SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }

        SlotLockBogoShortcutGuard.blockVanillaClickFor(slotlock$vanillaClickGuardMs);
    }

    /**
     * 服务端 / 内置服务器入口：
     * Alt + 左键移动整组物品。
     *
     * 改造目标：
     * 1. 来源是玩家背包时，跳过锁定槽，只移动未锁定槽
     * 2. 目标是玩家背包时，跳过锁定槽，只插入未锁定槽
     *
     * 注意：
     * 这里保持 BogoSorter 原本的 targetGroup。
     * 不额外加入快捷栏。
     */
    @Inject(
        method = "moveAllItems(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$moveAllItemsUnlockedOnly(EntityPlayer player, Container container,
        SlotAccessor hoveredSlot, boolean sameItemOnly, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (player == null || container == null || hoveredSlot == null) {
            return;
        }

        if (!BogoSortAPI.isValidSortable(container)) {
            return;
        }

        Slot currentSlot = container.getSlot(hoveredSlot.getSlotNumber());

        if (currentSlot == null) {
            return;
        }

        if (ShortcutHandler.SlotDummyOrCrafting(currentSlot)) {
            ci.cancel();
            return;
        }

        ItemStack filterStack = hoveredSlot.callGetStack();

        if (sameItemOnly && filterStack == null) {
            ci.cancel();
            return;
        }

        GuiSortingContext sortingContext = GuiSortingContext.getOrCreate(container);

        if (sortingContext == null || sortingContext.isEmpty()) {
            return;
        }

        SlotGroup sourceGroup = sortingContext.getSlotGroup(hoveredSlot.getSlotNumber());

        SlotGroup targetGroup = BogoSortAPI.isPlayerSlot(hoveredSlot) ? sortingContext.getNonPlayerSlotGroup()
            : sortingContext.getPlayerSlotGroup();

        if (sourceGroup == null || targetGroup == null || sourceGroup == targetGroup) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        List<SlotAccessor> sourceSlots = slotlock$filterSourceSlots(sourceGroup.getSlots());
        List<SlotAccessor> targetSlots = slotlock$filterTargetSlots(targetGroup.getSlots());

        if (sourceSlots.isEmpty() || targetSlots.isEmpty()) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        for (SlotAccessor sourceSlot : sourceSlots) {
            ItemStack stackInSlot = sourceSlot.callGetStack();

            if (stackInSlot == null) {
                continue;
            }

            if (sameItemOnly && !stackInSlot.isItemEqual(filterStack)) {
                continue;
            }

            ItemStack copy = stackInSlot.copy();

            ItemStack remainder = BogoSortAPI.insert(container, targetSlots, copy);

            if (remainder == null) {
                sourceSlot.callPutStack(null);
            } else {
                int inserted = stackInSlot.stackSize - remainder.stackSize;

                if (inserted > 0) {
                    sourceSlot.callPutStack(remainder.copy());
                }
            }
        }

        container.detectAndSendChanges();
        ci.cancel();
    }

    /**
     * 服务端 / 内置服务器入口：
     * 移动单个物品。
     */
    @Inject(
        method = "moveSingleItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$moveSingleItemUnlockedOnly(EntityPlayer player, Container container,
        SlotAccessor sourceSlot, boolean emptySlot, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (player == null || container == null || sourceSlot == null) {
            return;
        }

        ItemStack stack = sourceSlot.callGetStack();

        if (stack == null || stack.stackSize <= 0) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        if (slotlock$isLockedPlayerSlot(sourceSlot)) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        Slot currentSlot = container.getSlot(sourceSlot.getSlotNumber());

        if (currentSlot == null) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        if (ShortcutHandler.SlotDummyOrCrafting(currentSlot)) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        List<SlotAccessor> targetSlots = slotlock$getTargetSlotsForSingleMove(container, sourceSlot);

        if (targetSlots.isEmpty()) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        ItemStack toInsert = stack.copy();
        toInsert.stackSize = Math.min(1, stack.stackSize);

        ItemStack remainder = emptySlot ? BogoSortAPI.insert(container, targetSlots, toInsert, true)
            : BogoSortAPI.insert(container, targetSlots, toInsert);

        if (remainder == null) {
            ItemStack oldSource = stack.copy();

            ItemStack newSource = stack.copy();
            newSource.stackSize -= 1;

            if (newSource.stackSize <= 0) {
                newSource = null;
            }

            sourceSlot.callPutStack(newSource);
            sourceSlot.callOnSlotChange(oldSource, newSource);

            ItemStack picked = oldSource.copy();
            picked.stackSize = 1;
            sourceSlot.callOnPickupFromSlot(player, picked);
        }

        container.detectAndSendChanges();
        ci.cancel();
    }

    /**
     * 服务端 / 内置服务器入口：
     * 快捷丢弃。
     *
     * 锁定玩家槽不会被丢弃。
     */
    @Inject(
        method = "dropItems(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Container;Lcom/cleanroommc/bogosorter/mixins/early/minecraft/SlotAccessor;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void slotlock$dropItemsUnlockedOnly(EntityPlayer player, Container container,
        SlotAccessor hoveredSlot, boolean onlySameType, CallbackInfo ci) {
        if (!SlotLockManager.hasAnyLock()) {
            return;
        }

        if (player == null || container == null || hoveredSlot == null) {
            return;
        }

        ItemStack filterStack = hoveredSlot.callGetStack();

        if (onlySameType && filterStack == null) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        GuiSortingContext sortingContext = GuiSortingContext.getOrCreate(container);

        if (sortingContext == null || sortingContext.isEmpty()) {
            return;
        }

        SlotGroup sourceGroup = sortingContext.getSlotGroup(hoveredSlot.getSlotNumber());

        if (sourceGroup == null) {
            container.detectAndSendChanges();
            ci.cancel();
            return;
        }

        List<SlotAccessor> sourceSlots = slotlock$filterSourceSlots(sourceGroup.getSlots());

        for (SlotAccessor sourceSlot : sourceSlots) {
            ItemStack stackInSlot = sourceSlot.callGetStack();

            if (stackInSlot == null) {
                continue;
            }

            if (onlySameType && !stackInSlot.isItemEqual(filterStack)) {
                continue;
            }

            sourceSlot.callPutStack(null);
            player.dropPlayerItemWithRandomChoice(stackInSlot, true);
        }

        container.detectAndSendChanges();
        ci.cancel();
    }

    /**
     * 客户端预检查：
     * 判断这次 moveAll 是否存在合法目标。
     *
     * 如果没有合法目标，就在客户端吞掉点击。
     * 这样不会变成普通左键，也不会把物品拿到鼠标上。
     */
    @Unique
    private static boolean slotlock$clientMoveAllHasValidTarget(GuiContainer guiContainer, boolean sameItemOnly) {
        if (guiContainer == null || guiContainer.inventorySlots == null) {
            return false;
        }

        Container container = guiContainer.inventorySlots;

        SlotAccessor hoveredAccessor = ClientEventHandler.getSlot(guiContainer);

        if (hoveredAccessor == null) {
            return false;
        }

        ItemStack hoveredStack = hoveredAccessor.callGetStack();

        if (hoveredStack == null || hoveredStack.stackSize <= 0) {
            return false;
        }

        if (!BogoSortAPI.isValidSortable(container)) {
            return false;
        }

        GuiSortingContext sortingContext = GuiSortingContext.getOrCreate(container);

        if (sortingContext == null || sortingContext.isEmpty()) {
            return false;
        }

        SlotGroup sourceGroup = sortingContext.getSlotGroup(hoveredAccessor.getSlotNumber());

        SlotGroup targetGroup = BogoSortAPI.isPlayerSlot(hoveredAccessor) ? sortingContext.getNonPlayerSlotGroup()
            : sortingContext.getPlayerSlotGroup();

        if (sourceGroup == null || targetGroup == null || sourceGroup == targetGroup) {
            return false;
        }

        List<SlotAccessor> targetSlots = slotlock$filterTargetSlots(targetGroup.getSlots());

        if (targetSlots.isEmpty()) {
            return false;
        }

        ItemStack filterStack = hoveredAccessor.callGetStack();

        if (sameItemOnly && filterStack == null) {
            return false;
        }

        for (SlotAccessor sourceSlot : sourceGroup.getSlots()) {
            if (sourceSlot == null) {
                continue;
            }

            if (slotlock$isLockedPlayerSlot(sourceSlot)) {
                continue;
            }

            ItemStack sourceStack = sourceSlot.callGetStack();

            if (sourceStack == null || sourceStack.stackSize <= 0) {
                continue;
            }

            if (sameItemOnly && !sourceStack.isItemEqual(filterStack)) {
                continue;
            }

            if (slotlock$canInsertIntoAnyTarget(targetSlots, sourceStack)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 简单判断目标组里是否有可插入位置。
     *
     * 这里只做客户端预检查，防止无目标时变成普通左键。
     * 真正插入仍由 BogoSortAPI.insert 在服务端逻辑里处理。
     */
    @Unique
    private static boolean slotlock$canInsertIntoAnyTarget(List<SlotAccessor> targetSlots, ItemStack sourceStack) {
        if (targetSlots == null || sourceStack == null) {
            return false;
        }

        for (SlotAccessor targetSlot : targetSlots) {
            if (targetSlot == null) {
                continue;
            }

            if (slotlock$isLockedPlayerSlot(targetSlot)) {
                continue;
            }

            ItemStack targetStack = targetSlot.callGetStack();

            /*
             * 空槽：
             * 客户端预检查阶段认为可插入。
             * 具体是否合法交给服务端 BogoSortAPI.insert。
             */
            if (targetStack == null) {
                return true;
            }

            /*
             * 同类未满槽：
             * 可以合并。
             */
            if (!sourceStack.isStackable()) {
                continue;
            }

            if (!targetStack.isItemEqual(sourceStack)) {
                continue;
            }

            if (!ItemStack.areItemStackTagsEqual(targetStack, sourceStack)) {
                continue;
            }

            int max = targetStack.getMaxStackSize();

            if (targetStack.stackSize < max) {
                return true;
            }
        }

        return false;
    }

    /**
     * 来源槽过滤：
     * 如果来源是玩家背包，则移除锁定槽。
     * 如果来源不是玩家背包，则保留。
     */
    @Unique
    private static List<SlotAccessor> slotlock$filterSourceSlots(List<SlotAccessor> original) {
        List<SlotAccessor> result = new ArrayList<SlotAccessor>();

        if (original == null) {
            return result;
        }

        for (SlotAccessor slot : original) {
            if (slot == null) {
                continue;
            }

            if (slotlock$isLockedPlayerSlot(slot)) {
                continue;
            }

            result.add(slot);
        }

        return result;
    }

    /**
     * 目标槽过滤：
     * 如果目标是玩家背包，则移除锁定槽。
     * 如果目标不是玩家背包，则保留。
     */
    @Unique
    private static List<SlotAccessor> slotlock$filterTargetSlots(List<SlotAccessor> original) {
        List<SlotAccessor> result = new ArrayList<SlotAccessor>();

        if (original == null) {
            return result;
        }

        for (SlotAccessor slot : original) {
            if (slot == null) {
                continue;
            }

            if (slotlock$isLockedPlayerSlot(slot)) {
                continue;
            }

            result.add(slot);
        }

        return result;
    }

    /**
     * moveSingleItem 用：
     * 根据来源槽找到目标槽组，并过滤掉锁定玩家槽。
     *
     * 注意：
     * 这里保持 BogoSorter 原本 targetGroup。
     * 不额外加入快捷栏。
     */
    @Unique
    private static List<SlotAccessor> slotlock$getTargetSlotsForSingleMove(Container container,
        SlotAccessor sourceSlot) {
        List<SlotAccessor> result = new ArrayList<SlotAccessor>();

        if (container == null || sourceSlot == null) {
            return result;
        }

        if (BogoSortAPI.isValidSortable(container)) {
            GuiSortingContext sortingContext = GuiSortingContext.getOrCreate(container);

            if (sortingContext == null || sortingContext.isEmpty()) {
                return result;
            }

            SlotGroup sourceGroup = sortingContext.getSlotGroup(sourceSlot.getSlotNumber());

            SlotGroup targetGroup = BogoSortAPI.isPlayerSlot(sourceSlot) ? sortingContext.getNonPlayerSlotGroup()
                : sortingContext.getPlayerSlotGroup();

            if (sourceGroup == null || targetGroup == null || sourceGroup == targetGroup) {
                return result;
            }

            return slotlock$filterTargetSlots(targetGroup.getSlots());
        }

        boolean sourceIsPlayer = BogoSortAPI.isPlayerSlot(sourceSlot);

        for (Object object : container.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            if (ShortcutHandler.SlotDummyOrCrafting(slot)) {
                continue;
            }

            SlotAccessor accessor = BogoSortAPI.INSTANCE.getSlot(slot);

            if (accessor == null) {
                continue;
            }

            boolean targetIsPlayer = BogoSortAPI.isPlayerSlot(accessor);

            if (sourceIsPlayer == targetIsPlayer) {
                continue;
            }

            if (slotlock$isLockedPlayerSlot(accessor)) {
                continue;
            }

            result.add(accessor);
        }

        return result;
    }

    /**
     * 判断一个 BogoSorter SlotAccessor 是否对应 SlotLock 的锁定玩家背包槽。
     */
    @Unique
    private static boolean slotlock$isLockedPlayerSlot(SlotAccessor slot) {
        if (slot == null) {
            return false;
        }

        if (!BogoSortAPI.isPlayerSlot(slot)) {
            return false;
        }

        int playerIndex = slot.callGetSlotIndex();

        if (playerIndex < 0 || playerIndex > 35) {
            return false;
        }

        return SlotLockManager.isLockedPlayerIndex(playerIndex);
    }
}
