package com.mozilla.failsafe.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.mozilla.config.TaunahiConfig;
import com.mozilla.config.page.FailsafeNotificationsPage;
import com.mozilla.failsafe.Failsafe;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.impl.AutoReconnect;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.util.LogUtils;
import com.mozilla.util.helper.AudioManager;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.concurrent.TimeUnit;

public class JacobFailsafe extends Failsafe {
    private static JacobFailsafe instance;

    public static JacobFailsafe getInstance() {
        if (instance == null) {
            instance = new JacobFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 7;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.JACOB;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnJacobFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnJacobFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnJacobFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnJacobFailsafe;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (!TaunahiConfig.enableJacobFailsafes) return;
        if (!GameStateHandler.getInstance().getJacobsContestCrop().isPresent()) return;

        int cropThreshold = Integer.MAX_VALUE;
        switch (GameStateHandler.getInstance().getJacobsContestCrop().get()) {
            case CARROT:
                cropThreshold = TaunahiConfig.jacobCarrotCap;
                break;
            case NETHER_WART:
                cropThreshold = TaunahiConfig.jacobNetherWartCap;
                break;
            case POTATO:
                cropThreshold = TaunahiConfig.jacobPotatoCap;
                break;
            case WHEAT:
                cropThreshold = TaunahiConfig.jacobWheatCap;
                break;
            case SUGAR_CANE:
                cropThreshold = TaunahiConfig.jacobSugarCaneCap;
                break;
            case MELON:
                cropThreshold = TaunahiConfig.jacobMelonCap;
                break;
            case PUMPKIN:
                cropThreshold = TaunahiConfig.jacobPumpkinCap;
                break;
            case CACTUS:
                cropThreshold = TaunahiConfig.jacobCactusCap;
                break;
            case COCOA_BEANS:
                cropThreshold = TaunahiConfig.jacobCocoaBeansCap;
                break;
            case MUSHROOM_ROTATE:
            case MUSHROOM:
                cropThreshold = TaunahiConfig.jacobMushroomCap;
                break;
        }

        if (GameStateHandler.getInstance().getJacobsContestCropNumber() >= cropThreshold) {
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        if (TaunahiConfig.jacobFailsafeAction) {
            // pause
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Paused the macro because of extended Jacob's Content!", false);
                MacroHandler.getInstance().pauseMacro();
            } else if (!GameStateHandler.getInstance().inJacobContest()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because Jacob's Contest is over!", false);
                endOfFailsafeTrigger();
            }
        } else {
            // leave
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Leaving because of extending Jacob's Contest!", false);
                MacroHandler.getInstance().pauseMacro();
                Multithreading.schedule(() -> {
                    try {
                        mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText("Will reconnect after end of Jacob's Contest! Restart if you dont see a timer"));
                        AudioManager.getInstance().resetSound();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 500, TimeUnit.MILLISECONDS);
            } else if (GameStateHandler.getInstance().getJacobContestLeftClock().passed()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because Jacob's Contest is over!", false);
                AutoReconnect.getInstance().start();
            }
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        MacroHandler.getInstance().resumeMacro();
    }
}
