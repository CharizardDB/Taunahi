package com.mozilla.failsafe.impl;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.config.page.FailsafeNotificationsPage;
import com.mozilla.failsafe.Failsafe;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.FeatureManager;
import com.mozilla.feature.impl.BPSTracker;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.handler.RotationHandler;
import com.mozilla.util.KeyBindUtils;
import com.mozilla.util.helper.Clock;
import com.mozilla.util.helper.Rotation;
import com.mozilla.util.helper.RotationConfiguration;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LowerAvgBpsFailsafe extends Failsafe {
    private static LowerAvgBpsFailsafe instance;

    private final Clock clock = new Clock();
    private long lastTriggered = 0L;

    public static LowerAvgBpsFailsafe getInstance() {
        if (instance == null) {
            instance = new LowerAvgBpsFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 9;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.LOWER_AVERAGE_BPS;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnLowerAverageBPS;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnLowerAverageBPS;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnLowerAverageBPS;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnLowerAverageBPS;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        float currentBPS = BPSTracker.getInstance().getBPSFloat();
        boolean shouldReset = FeatureManager.getInstance().shouldPauseMacroExecution()
                || BPSTracker.getInstance().dontCheckForBPS()
                || !TaunahiConfig.enableBpsCheck
                || currentBPS >= TaunahiConfig.minBpsThreshold;

        if (shouldReset) {
            if (clock.isScheduled() || lowerBPSState == LowerBPSState.WAIT_BEFORE_START) {
                resetStates();
                // LogUtils.sendDebug("LowerAvgBpsFailsafe: Reset states. Current BPS: " + currentBPS);
            }
            return;
        }

        if (!clock.isScheduled()) {
            clock.schedule(4500L + Math.random() * 1000L);
            // LogUtils.sendDebug("LowerAvgBpsFailsafe: BPS below threshold. Current: " + currentBPS + ", Threshold: " + TaunahiConfig.minBpsThreshold);
        } else if (clock.passed()) {
            if (System.currentTimeMillis() - lastTriggered < 60_000L) {
                resetStates();
                return;
            }
            // LogUtils.sendDebug("LowerAvgBpsFailsafe: Failsafe triggered. Current BPS: " + currentBPS);
            lastTriggered = System.currentTimeMillis();
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        switch (lowerBPSState) {
            case NONE:
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                lowerBPSState = LowerBPSState.WAIT_BEFORE_START;
                break;
            case WAIT_BEFORE_START:
                KeyBindUtils.stopMovement();
                MacroHandler.getInstance().pauseMacro();
                FailsafeManager.getInstance().scheduleRandomDelay(500, 500);
                lowerBPSState = LowerBPSState.WARP_BACK;
                break;
            case WARP_BACK:
                if (GameStateHandler.getInstance().inGarden()) {
                    MacroHandler.getInstance().triggerWarpGarden(true, false);
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                    lowerBPSState = LowerBPSState.END;
                } else {
                    if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.HUB) {
                        MacroHandler.getInstance().triggerWarpGarden(true, false);
                        FailsafeManager.getInstance().scheduleRandomDelay(2500, 2000);
                    } else if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.LIMBO) {
                        mc.thePlayer.sendChatMessage("/lobby");
                        FailsafeManager.getInstance().scheduleRandomDelay(2500, 2000);
                    } else {
                        mc.thePlayer.sendChatMessage("/skyblock");
                        FailsafeManager.getInstance().scheduleRandomDelay(5500, 4000);
                    }
                }
                break;
            case END:
                float randomTime = TaunahiConfig.getRandomRotationTime();
                this.rotation.easeTo(
                        new RotationConfiguration(
                                new Rotation(
                                        (float) (mc.thePlayer.rotationYaw + Math.random() * 60 - 30),
                                        (float) (30 + Math.random() * 20 - 10))
                                , (long) randomTime, null));
                FailsafeManager.getInstance().stopFailsafes();
                FailsafeManager.getInstance().restartMacroAfterDelay();
                this.endOfFailsafeTrigger();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        lastTriggered = System.currentTimeMillis();
    }

    @Override
    public void resetStates() {
        clock.reset();
        lowerBPSState = LowerBPSState.NONE;
    }

    enum LowerBPSState {
        NONE,
        WAIT_BEFORE_START,
        WARP_BACK,
        END
    }

    private LowerBPSState lowerBPSState = LowerBPSState.NONE;
    private final RotationHandler rotation = RotationHandler.getInstance();
}
