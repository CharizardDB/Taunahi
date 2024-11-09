package com.mozilla.failsafe.impl;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.config.page.FailsafeNotificationsPage;
import com.mozilla.failsafe.Failsafe;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.impl.Scheduler;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.util.LogUtils;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Optional;

public class EvacuateFailsafe extends Failsafe {
    private static EvacuateFailsafe instance;

    public static EvacuateFailsafe getInstance() {
        if (instance == null) {
            instance = new EvacuateFailsafe();
        }
        return instance;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public FailsafeManager.EmergencyType getType() {
        return FailsafeManager.EmergencyType.EVACUATE;
    }

    @Override
    public boolean shouldSendNotification() {
        return FailsafeNotificationsPage.notifyOnEvacuateFailsafe;
    }

    @Override
    public boolean shouldPlaySound() {
        return FailsafeNotificationsPage.alertOnEvacuateFailsafe;
    }

    @Override
    public boolean shouldTagEveryone() {
        return FailsafeNotificationsPage.tagEveryoneOnEvacuateFailsafe;
    }

    @Override
    public boolean shouldAltTab() {
        return FailsafeNotificationsPage.autoAltTabOnEvacuateFailsafe;
    }

    @Override
    public void onTickDetection(TickEvent.ClientTickEvent event) {
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!TaunahiConfig.autoEvacuateOnServerReboot) return;
        if (evacuateState != EvacuateState.NONE) return;

        GameStateHandler.getInstance().getServerClosingSeconds().ifPresent(seconds -> {
            if (seconds < 30) {
                FailsafeManager.getInstance().possibleDetection(this);
            }
        });
    }

    @Override
    public void onChatDetection(ClientChatReceivedEvent event) {
        String msg = StringUtils.stripControlCodes(event.message.getUnformattedText());
        if (msg.startsWith("You can't use this when the server is about to")) {
            FailsafeManager.getInstance().possibleDetection(this);
        }
    }

    @Override
    public void duringFailsafeTrigger() {
        switch (evacuateState) {
            case NONE:
                MacroHandler.getInstance().pauseMacro();
                MacroHandler.getInstance().getCurrentMacro().ifPresent(am -> am.setSavedState(Optional.empty()));
                evacuateState = EvacuateState.EVACUATE_FROM_ISLAND;
                FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                break;
            case EVACUATE_FROM_ISLAND:
                if (GameStateHandler.getInstance().inGarden()) {
                    mc.thePlayer.sendChatMessage("/evacuate");
                    FailsafeManager.getInstance().scheduleRandomDelay(2500, 2000);
                } else {
                    evacuateState = EvacuateState.TP_BACK_TO_ISLAND;
                    FailsafeManager.getInstance().scheduleRandomDelay(3000, 3000);
                }
                break;
            case TP_BACK_TO_ISLAND:
                if (GameStateHandler.getInstance().inGarden()) {
                    evacuateState = EvacuateState.END;
                    FailsafeManager.getInstance().scheduleRandomDelay(500, 1000);
                } else {
                    if (GameStateHandler.getInstance().getLocation() == GameStateHandler.Location.HUB) {
                        MacroHandler.getInstance().triggerWarpGarden(true, false);
                        FailsafeManager.getInstance().scheduleRandomDelay(5500, 2000);
                    } else {
                        mc.thePlayer.sendChatMessage("/skyblock");
                        FailsafeManager.getInstance().scheduleRandomDelay(60_000, 4000);
                    }
                }
                break;
            case END:
                LogUtils.sendFailsafeMessage("[Failsafe] Came back from evacuation!");
                endOfFailsafeTrigger();
                break;
        }
    }

    @Override
    public void endOfFailsafeTrigger() {
        FailsafeManager.getInstance().stopFailsafes();
        FailsafeManager.getInstance().setHadEmergency(false);
        if (Scheduler.getInstance().isFarming())
            MacroHandler.getInstance().resumeMacro();
    }

    @Override
    public void resetStates() {
        evacuateState = EvacuateState.NONE;
    }

    private EvacuateState evacuateState = EvacuateState.NONE;

    enum EvacuateState {
        NONE,
        EVACUATE_FROM_ISLAND,
        TP_BACK_TO_ISLAND,
        END
    }
}
