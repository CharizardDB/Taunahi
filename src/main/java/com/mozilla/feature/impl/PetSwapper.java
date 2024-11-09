package com.mozilla.feature.impl;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.feature.FeatureManager;
import com.mozilla.feature.IFeature;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.util.InventoryUtils;
import com.mozilla.util.KeyBindUtils;
import com.mozilla.util.LogUtils;
import com.mozilla.util.PlayerUtils;
import com.mozilla.util.helper.Clock;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

public class PetSwapper implements IFeature {
    public static State currentState = State.NONE;
    public static boolean hasPetChangedDuringThisContest = false;
    static Clock delayClock = new Clock();
    static String previousPet = null;
    private static PetSwapper instance;
    private static boolean getPreviousPet = false;
    private final Minecraft mc = Minecraft.getMinecraft();
    List<ItemStack> inventory;
    @Setter
    private boolean enabled;
    private boolean dontEnableUntilEndOfContest = false;

    public static PetSwapper getInstance() {
        if (instance == null) {
            instance = new PetSwapper();
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Pet Swapper";
    }

    @Override
    public boolean isRunning() {
        return enabled;
    }

    @Override
    public boolean shouldPauseMacroExecution() {
        return true;
    }

    @Override
    public boolean shouldStartAtMacroStart() {
        return false;
    }

    @Override
    public void start() {
        start(false);
        IFeature.super.start();
    }

    @Override
    public void stop() {
        if (enabled)
            LogUtils.sendWarning("[Pet Swapper] Disabled!");
        enabled = false;
        inventory = null;
        currentState = State.NONE;
        delayClock.reset();
        PlayerUtils.closeScreen();
        KeyBindUtils.stopMovement();
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().resumeMacro();
        }
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        currentState = State.NONE;
        previousPet = null;
        getPreviousPet = false;
        hasPetChangedDuringThisContest = false;
        dontEnableUntilEndOfContest = false;
    }

    @Override
    public boolean isToggled() {
        return TaunahiConfig.enablePetSwapper;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    public void start(boolean getPreviousPet) {
        if (enabled) return;
        PlayerUtils.closeScreen();
        LogUtils.sendWarning("[Pet Swapper] Starting...");
        currentState = State.STARTING;
        enabled = true;
        PetSwapper.getPreviousPet = getPreviousPet;
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
        }
    }

    @SubscribeEvent
    public void onTickShouldEnable(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!isToggled()) return;
        if (isRunning()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (GameStateHandler.getInstance().getCookieBuffState() != GameStateHandler.BuffState.ACTIVE) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;
        if (dontEnableUntilEndOfContest) {
            if (!GameStateHandler.getInstance().inJacobContest()) {
                dontEnableUntilEndOfContest = false;
            } else {
                return;
            }
        }
        if (!GameStateHandler.getInstance().inJacobContest()) {
            if (hasPetChangedDuringThisContest) {
                hasPetChangedDuringThisContest = false;
                start(true);
            }
            return;
        }
        if (hasPetChangedDuringThisContest) return;
        if (TaunahiConfig.petSwapperName.trim().isEmpty()) {
            LogUtils.sendError("[Pet Swapper] You have not set a pet name in the settings! Disabling this feature...");
            TaunahiConfig.enablePetSwapper = false;
            return;
        }
        start();
    }

    @SubscribeEvent
    public void onTickEnabled(TickEvent.ClientTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!isToggled()) return;
        if (!isRunning()) return;
        if (!GameStateHandler.getInstance().inGarden()) return;
        if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) return;

        if (delayClock.isScheduled() && !delayClock.passed()) return;

        switch (currentState) {
            case STARTING:
                if (mc.currentScreen != null) return;
                LogUtils.sendDebug("[Pet Swapper] starting");
                mc.thePlayer.sendChatMessage("/pets");
                if (previousPet != null && !previousPet.isEmpty() && !getPreviousPet) {
                    currentState = State.FIND_NEW;
                } else {
                    currentState = State.FIND_PREVIOUS;
                }
                delayClock.schedule(TaunahiConfig.petSwapperDelay);
                break;
            case FIND_PREVIOUS:
                LogUtils.sendDebug("[Pet Swapper] waiting for pets menu");
                if (!(mc.thePlayer.openContainer instanceof ContainerChest)) return;
                inventory = mc.thePlayer.openContainer.getInventory();
                for (ItemStack itemStack : inventory) {
                    if (itemStack == null) continue;
                    List<String> petLore = InventoryUtils.getItemLore(itemStack);
                    String petName = StringUtils.stripControlCodes(itemStack.getDisplayName());
                    if (petName.contains("]")) {
                        petName = petName.substring(petName.indexOf("]") + 2);
                    }
                    if (getPreviousPet) {
                        if (petName.toLowerCase().trim().contains(previousPet.toLowerCase())) {
                            LogUtils.sendDebug("[Pet Swapper] found previous pet: " + petName);
                            InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer(petName), InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                            currentState = State.WAITING_FOR_SPAWN;
                            delayClock.schedule(TaunahiConfig.petSwapperDelay);
                            return;
                        }
                        if (petName.toLowerCase().contains("next page")) {
                            InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer("next page"), InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                            delayClock.schedule(TaunahiConfig.petSwapperDelay);
                            return;
                        }
                        continue;
                    }
                    if (petLore.stream().anyMatch(s -> s.toLowerCase().contains("click to despawn"))) {
                        if (petName.toLowerCase().trim().contains(TaunahiConfig.petSwapperName.toLowerCase())) {
                            LogUtils.sendError("The current pet is already the one we want! The pet won't be swapped at the end of this contest.");
                            hasPetChangedDuringThisContest = false;
                            dontEnableUntilEndOfContest = true;
                            PlayerUtils.closeScreen();
                            stop();
                            return;
                        }
                        previousPet = petName.toLowerCase().trim();
                        LogUtils.sendDebug("[Pet Swapper] previous pet: " + previousPet);
                        currentState = State.FIND_NEW;
                        delayClock.schedule(TaunahiConfig.petSwapperDelay);
                        break;
                    }
                    if (petName.toLowerCase().contains("next page")) {
                        InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer("next page"), InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        delayClock.schedule(TaunahiConfig.petSwapperDelay);
                        return;
                    }
                }
                if (previousPet == null) {
                    LogUtils.sendError("[Pet Swapper] no previous pet found, disabling...");
                    TaunahiConfig.enablePetSwapper = false;
                    hasPetChangedDuringThisContest = false;
                    PlayerUtils.closeScreen();
                    stop();
                    return;
                }
                break;
            case FIND_NEW:
                LogUtils.sendDebug("[Pet Swapper] waiting for pets menu");
                if (!(mc.thePlayer.openContainer instanceof ContainerChest)) return;
                inventory = mc.thePlayer.openContainer.getInventory();

                for (ItemStack itemStack : inventory) {
                    if (itemStack == null) continue;
                    String petName = StringUtils.stripControlCodes(itemStack.getDisplayName());
                    if (petName.contains("]")) {
                        petName = petName.substring(petName.indexOf("]") + 2);
                    }
                    if (petName.toLowerCase().trim().contains(TaunahiConfig.petSwapperName.toLowerCase())) {
                        LogUtils.sendDebug("[Pet Swapper] found new pet: " + petName);
                        InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer(petName), InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        currentState = State.WAITING_FOR_SPAWN;
                        delayClock.schedule(TaunahiConfig.petSwapperDelay);
                        return;
                    }
                    if (petName.toLowerCase().contains("next page")) {
                        InventoryUtils.clickContainerSlot(InventoryUtils.getSlotIdOfItemInContainer("next page"), InventoryUtils.ClickType.LEFT, InventoryUtils.ClickMode.PICKUP);
                        delayClock.schedule(TaunahiConfig.petSwapperDelay);
                        return;
                    }
                }
                LogUtils.sendError("[Pet Swapper] Could not find the new pet! Disabling this feature...");
                TaunahiConfig.enablePetSwapper = false;
                hasPetChangedDuringThisContest = false;
                PlayerUtils.closeScreen();
                stop();
                break;
            case WAITING_FOR_SPAWN:
                break;
            case NONE:
                stop();
                break;
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onChatMessageReceived(ClientChatReceivedEvent event) {
        if (event.type == 0 && event.message != null && previousPet != null && TaunahiConfig.petSwapperName != null) {
            String msg = StringUtils.stripControlCodes(event.message.getUnformattedText());
            if (msg.contains(":")) return;
            String spawnMessage = "you summoned your " + (getPreviousPet ? previousPet : TaunahiConfig.petSwapperName).toLowerCase();
            if (msg.toLowerCase().contains(spawnMessage)) {
                if (!isRunning() || currentState != State.WAITING_FOR_SPAWN) {
                    return;
                }
                currentState = State.NONE;
                LogUtils.sendDebug("[Pet Swapper] pet spawned");
                delayClock.schedule(1000);
                if (GameStateHandler.getInstance().inJacobContest()) {
                    hasPetChangedDuringThisContest = true;
                }
            }
        }
    }

    @SubscribeEvent
    public final void onUnloadWorld(WorldEvent.Unload event) {
        resetStatesAfterMacroDisabled();
    }

    public enum State {
        NONE,
        STARTING,
        FIND_PREVIOUS,
        FIND_NEW,
        WAITING_FOR_SPAWN
    }
}
