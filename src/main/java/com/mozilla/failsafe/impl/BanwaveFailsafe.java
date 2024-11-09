package com.mozilla.failsafe.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.mozilla.config.TaunahiConfig;
import com.mozilla.config.page.FailsafeNotificationsPage;
import com.mozilla.event.ReceivePacketEvent;
import com.mozilla.failsafe.Failsafe;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.impl.AutoReconnect;
import com.mozilla.feature.impl.BanInfoWS;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.util.LogUtils;
import com.mozilla.util.helper.AudioManager;
import net.minecraft.util.ChatComponentText;

import java.util.concurrent.TimeUnit;

public class BanwaveFailsafe extends Failsafe {
    private static BanwaveFailsafe instance;
    public static BanwaveFailsafe getInstance() {
        if (instance == null) {
            instance = new BanwaveFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 6;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.BANWAVE;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnBanwaveFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnBanwaveFailsafe;
    }

    @Override
    public void duringFailsafeTrigger() {
        if (TaunahiConfig.banwaveAction) {
            // pause
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Paused the macro because of banwave!", false);
                MacroHandler.getInstance().pauseMacro();
            } else {
                if (!BanInfoWS.getInstance().isBanwave()) {
                    endOfFailsafeTrigger();
                }
            }
        } else {
            // leave
            if (!MacroHandler.getInstance().isCurrentMacroPaused()) {
                LogUtils.sendFailsafeMessage("[Failsafe] Leaving because of banwave!", false);
                MacroHandler.getInstance().pauseMacro();
                Multithreading.schedule(() -> {
                    try {
                        mc.getNetHandler().getNetworkManager().closeChannel(new ChatComponentText("Will reconnect after end of banwave!"));
                        AudioManager.getInstance().resetSound();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 500, TimeUnit.MILLISECONDS);
            } else {
                if (!BanInfoWS.getInstance().isBanwave()) {
                    LogUtils.sendFailsafeMessage("[Failsafe] Reconnecting because banwave ended", false);
                    AutoReconnect.getInstance().start();
                }
            }
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        LogUtils.sendFailsafeMessage("[Failsafe] Resuming the macro because banwave is over!", false);
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        MacroHandler.getInstance().resumeMacro();
    }

    @Override
    public void onReceivedPacketDetection(ReceivePacketEvent event) {
        if (!BanInfoWS.getInstance().isBanwave()) return;
        if (!TaunahiConfig.banwaveCheckerEnabled) return;
        if (!TaunahiConfig.enableLeavePauseOnBanwave) return;
        if (TaunahiConfig.banwaveDontLeaveDuringJacobsContest && GameStateHandler.getInstance().inJacobContest())
            return;
        if (!MacroHandler.getInstance().getMacro().isEnabledAndNoFeature()) return;
        FailsafeManager.getInstance().possibleDetection(this);
    }
}
