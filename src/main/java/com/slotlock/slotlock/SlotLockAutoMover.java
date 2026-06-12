package com.slotlock.slotlock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class SlotLockAutoMover {

    /**
     * 记录锁定槽在“开始观察时”的内容。
     *
     * key = 玩家背包 index，0-35
     * value = 当时的 ItemStack 复制
     *
     * value 为 null 表示：
     * 这个槽在开始观察时是空的。
     * 后来如果服务器把物品塞进来，就可以尝试自动搬走。
     */
    private final Map<Integer, ItemStack> expectedStacks = new HashMap<Integer, ItemStack>();

    /**
     * 搬运状态机。
     */
    private enum MoveState {
        IDLE,
        WAITING_TO_PLACE,
        COOLDOWN
    }

    private MoveState state = MoveState.IDLE;
    private int stateTicks = 0;

    private int pendingWindowId = -1;
    private int pendingSlotNumber = -1;
    private int pendingTargetPlayerIndex = -1;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            resetAll();
            return;
        }

        EntityPlayer player = mc.thePlayer;

        /*
         * 如果已经进入“拿起物品，等待放下”的状态，
         * 优先完成搬运。
         * 不要在这里因为打开 GUI 而直接清空状态，
         * 否则可能出现鼠标上已经拿起物品，但是没有放下的情况。
         */
        if (state == MoveState.WAITING_TO_PLACE) {
            handleWaitingToPlace(mc, player);
            return;
        }

        /*
         * 创造模式不要自动搬。
         * 打开 GUI 时也不要启动新的自动搬运。
         * 注意：
         * 这里只重置搬运状态，不清空 expectedStacks。
         * 否则打开一次背包后，锁定空槽的“原本为空”记录会丢失。
         */
        if (mc.currentScreen != null || (mc.playerController != null && mc.playerController.isInCreativeMode())) {
            resetMoveState();
            return;
        }

        if (state == MoveState.COOLDOWN) {
            if (stateTicks > 0) {
                stateTicks--;
                return;
            }

            state = MoveState.IDLE;
        }

        /*
         * IDLE 状态下，只有鼠标上没有物品时，才允许启动新的自动搬运。
         */
        if (player.inventory.getItemStack() != null) {
            return;
        }

        syncExpectedSlots(player.inventory);

        for (int i = 0; i < 36; i++) {
            if (!SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            ItemStack expected = expectedStacks.get(Integer.valueOf(i));
            ItemStack current = player.inventory.getStackInSlot(i);

            /*
             * 只处理：
             * 锁定时为空，后来服务器把物品塞进来的情况。
             * 如果锁定时本来就有物品，不自动搬走。
             * 否则可能把玩家原本想保护的工具/武器搬走。
             */
            if (expected == null && current != null) {
                boolean started = startMoveLockedSlot(player, i);

                if (started) {
                    return;
                }
            }
        }
    }

    /**
     * 只重置搬运状态，不清空 expectedStacks。
     */
    private void resetMoveState() {
        state = MoveState.IDLE;
        stateTicks = 0;

        pendingWindowId = -1;
        pendingSlotNumber = -1;
        pendingTargetPlayerIndex = -1;
    }

    /**
     * 完全重置。
     *
     * 世界为空、玩家为空、退出游戏时使用。
     */
    private void resetAll() {
        expectedStacks.clear();
        resetMoveState();
    }

    /**
     * 处理“已经拿起物品，等待放下”的阶段。
     */
    private void handleWaitingToPlace(Minecraft mc, EntityPlayer player) {
        stateTicks--;

        if (stateTicks > 0) {
            return;
        }

        /*
         * 放下前再次检查：
         * 1. 鼠标上确实有物品
         * 2. 目标 window / slot 有效
         * 3. 目标玩家背包 index 有效
         * 4. 目标槽现在仍然是空的
         * 如果目标槽这几 tick 内被服务器填了，
         * 不强行放下，避免变成交换物品。
         */
        if (player.inventory.getItemStack() != null && pendingWindowId != -1
            && pendingSlotNumber != -1
            && pendingTargetPlayerIndex != -1
            && player.inventory.getStackInSlot(pendingTargetPlayerIndex) == null) {

            mc.playerController.windowClick(pendingWindowId, pendingSlotNumber, 0, 0, player);
        }

        state = MoveState.COOLDOWN;
        stateTicks = 10;

        pendingWindowId = -1;
        pendingSlotNumber = -1;
        pendingTargetPlayerIndex = -1;
    }

    /**
     * 启动搬运逻辑。
     *
     * 这里只执行第一步：从锁定槽拿起物品。
     * 第二步放下会在几 tick 后执行。
     */
    private boolean startMoveLockedSlot(EntityPlayer player, int lockedPlayerIndex) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.playerController == null) {
            return false;
        }

        if (player.inventory.getItemStack() != null) {
            return false;
        }

        int targetPlayerIndex = findUnlockedEmptySlot(player.inventory);

        if (targetPlayerIndex < 0) {
            return false;
        }

        Container container = player.inventoryContainer;

        Slot fromSlot = findPlayerSlot(container, player.inventory, lockedPlayerIndex);
        Slot toSlot = findPlayerSlot(container, player.inventory, targetPlayerIndex);

        if (fromSlot == null || toSlot == null) {
            return false;
        }

        if (!fromSlot.getHasStack()) {
            return false;
        }

        if (toSlot.getHasStack()) {
            return false;
        }

        /*
         * 第一步：
         * 左键拿起锁定槽里的物品。
         */
        mc.playerController.windowClick(container.windowId, fromSlot.slotNumber, 0, 0, player);

        /*
         * 第二步：
         * 延迟几个 tick 后，再放到未锁定空槽。
         */
        pendingWindowId = container.windowId;
        pendingSlotNumber = toSlot.slotNumber;
        pendingTargetPlayerIndex = targetPlayerIndex;

        state = MoveState.WAITING_TO_PLACE;
        stateTicks = 3;

        return true;
    }

    private void syncExpectedSlots(InventoryPlayer inventory) {
        /*
         * 删除已经不再锁定的槽。
         */
        Iterator<Integer> iterator = expectedStacks.keySet()
            .iterator();

        while (iterator.hasNext()) {
            int index = iterator.next()
                .intValue();

            if (!SlotLockManager.isLockedPlayerIndex(index)) {
                iterator.remove();
            }
        }

        /*
         * 新锁定的槽，记录当前内容。
         */
        for (int i = 0; i < 36; i++) {
            if (!SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            Integer key = Integer.valueOf(i);

            if (!expectedStacks.containsKey(key)) {
                ItemStack stack = inventory.getStackInSlot(i);
                expectedStacks.put(key, stack == null ? null : stack.copy());
            }
        }
    }

    /**
     * 找一个未锁定的空槽。
     *
     * 优先放进主背包 9-35，
     * 最后才放进快捷栏 0-8。
     */
    private int findUnlockedEmptySlot(InventoryPlayer inventory) {
        for (int i = 9; i < 36; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (inventory.getStackInSlot(i) == null) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (SlotLockManager.isLockedPlayerIndex(i)) {
                continue;
            }

            if (inventory.getStackInSlot(i) == null) {
                return i;
            }
        }

        return -1;
    }

    private Slot findPlayerSlot(Container container, InventoryPlayer inventory, int playerIndex) {
        for (Object object : container.inventorySlots) {
            if (!(object instanceof Slot)) {
                continue;
            }

            Slot slot = (Slot) object;

            if (slot.isSlotInInventory(inventory, playerIndex)) {
                return slot;
            }
        }

        return null;
    }
}
