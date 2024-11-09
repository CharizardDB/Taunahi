package com.mozilla.feature.impl;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.FeatureManager;
import com.mozilla.feature.IFeature;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.handler.RotationHandler;
import com.mozilla.util.InventoryUtils;
import com.mozilla.util.KeyBindUtils;
import com.mozilla.util.LogUtils;
import com.mozilla.util.PlayerUtils;
import com.mozilla.util.helper.Clock;
import com.mozilla.util.helper.RotationConfiguration;
import com.mozilla.util.helper.Target;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Tuple;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class PestsDestroyerOnTheTrack implements IFeature {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static PestsDestroyerOnTheTrack instance;

    public static PestsDestroyerOnTheTrack getInstance() {
        if (instance == null) {
            instance = new PestsDestroyerOnTheTrack();
        }
        return instance;
    }

    private boolean isRunning = false;

    @Getter
    private final Clock delayStart = new Clock();
    @Getter
    private final Clock stuckTimer = new Clock();

    private Entity currentTarget = null;

    @Override
    public String getName() {
        return "Pests Destroyer On The Track";
    }

    @Override
    public boolean isRunning() {
        return isRunning;
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
        isRunning = true;
        currentTarget = null;
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().pauseMacro();
        }
        if (getVacuum()) return;
        LogUtils.sendWarning("[" + getName() + "] Started!");
        stuckTimer.schedule(TaunahiConfig.pestsDestroyerOnTheTrackStuckTimer);
        IFeature.super.start();
    }

    @Override
    public void stop() {
        isRunning = false;
        LogUtils.sendWarning("[" + getName() + "] Stopped!");
        if (MacroHandler.getInstance().isMacroToggled()) {
            MacroHandler.getInstance().resumeMacro();
            PlayerUtils.getTool();
        }
        delayStart.reset();
        stuckTimer.reset();
        noPestTimer.reset();
        IFeature.super.stop();
    }

    @Override
    public void resetStatesAfterMacroDisabled() {
        isRunning = false;
        delayStart.reset();
        stuckTimer.reset();
    }

    @Override
    public boolean isToggled() {
        return TaunahiConfig.pestsDestroyerOnTheTrack;
    }

    @Override
    public boolean shouldCheckForFailsafes() {
        return false;
    }

    @SubscribeEvent
    public void onTickShouldEnable(TickEvent.PlayerTickEvent event) {
        if (mc.thePlayer == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (FeatureManager.getInstance().shouldPauseMacroExecution()) return;
        if (MacroHandler.getInstance().isCurrentMacroPaused()) return;
        if (FailsafeManager.getInstance().triggeredFailsafe.isPresent()) return;
        if (PestsDestroyer.getInstance().isRunning()) return;
        if (GameStateHandler.getInstance().inJacobContest() && TaunahiConfig.dontKillPestsOnTrackDuringJacobsContest)
            return;
        if (isRunning()) return;
        if (!isToggled()) return;

        if (getPest(true).isPresent()) {
            if (!delayStart.isScheduled()) {
                delayStart.schedule(TaunahiConfig.pestsDestroyerOnTheTrackTimeForPestToStayInRange);
                LogUtils.sendDebug("[" + getName() + "] Found pest, waiting for him to stay in range!");
            }
            if (delayStart.isScheduled() && delayStart.passed()) {
                start();
            }
        } else {
            if (delayStart.isScheduled()) {
                delayStart.reset();
                LogUtils.sendDebug("[" + getName() + "] Pest left!");
            }
        }
    }

    private final Clock noPestTimer = new Clock();

    @SubscribeEvent
    public void onTickExecution(TickEvent.PlayerTickEvent event) {
        if (mc.thePlayer == null) return;
        if (!MacroHandler.getInstance().isMacroToggled()) return;
        if (!isRunning()) return;
        if (!isToggled()) {
            stop();
            return;
        }

        if (stuckTimer.isScheduled() && stuckTimer.passed()) {
            LogUtils.sendWarning("[" + getName() + "] Stuck for too long, stopping!");
            stop();
            return;
        }

        if (getVacuum()) return;

        Optional<Entity> entity = getPest(false);
        if (entity.isPresent()) {
            if (currentTarget == null || !currentTarget.equals(entity.get())) {
                currentTarget = entity.get();
                stuckTimer.schedule(TaunahiConfig.pestsDestroyerOnTheTrackStuckTimer);
            }
            noPestTimer.reset();
            KeyBindUtils.holdThese(mc.gameSettings.keyBindUseItem);
            if (!RotationHandler.getInstance().isRotating()) {
                RotationHandler.getInstance().easeTo(
                        new RotationConfiguration(
                                new Target(entity.get()),
                                TaunahiConfig.getRandomRotationTime(),
                                null
                        )
                );
            }
        } else {
            if (!noPestTimer.isScheduled()) {
                noPestTimer.schedule(500);
            }
            if (noPestTimer.isScheduled() && noPestTimer.passed()) {
                LogUtils.sendWarning("[" + getName() + "] No pests found, stopping!");
                stop();
            }
        }
    }

    private Optional<Entity> getPest(boolean start) {
        List<Entity> entities = PestsDestroyer.getInstance().getPestsLocations();
        float vacuumRange = PestsDestroyer.getInstance().getCurrentVacuumRange();
        List<Tuple<Entity, Double>> temp = new ArrayList<>();

        Optional<Entity> opt = entities.stream()
                .filter(e -> {
                    Vec3 entityPosition = new Vec3(e.posX, e.posY + e.getEyeHeight(), e.posZ);
                    Vec3 playerPosition = mc.thePlayer.getPositionEyes(1);
                    double dist = playerPosition.distanceTo(entityPosition);
                    boolean returnResult;
                    if (start) {
                        double xDiff = entityPosition.xCoord - playerPosition.xCoord;
                        double zDiff = entityPosition.zCoord - playerPosition.zCoord;

                        float yaw = (float) Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90F;
                        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - MathHelper.wrapAngleTo180_float(yaw));
                        if (TaunahiConfig.showDebugLogsAboutPDOTT)
                            LogUtils.sendDebug("Entity Pos: " + e.getPositionVector() + " | CurrenYaw: " + MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) + " | YawNeeded: " + yaw + " | YawDiff: " + yawDiff + " | Dist: " + dist);
                        returnResult = dist <= vacuumRange - 0.5 && yawDiff <= TaunahiConfig.pestsDestroyerOnTheTrackFOV / 2f;
                    } else {
                        returnResult = dist <= vacuumRange - 0.5;
                    }
                    if (returnResult) {
                        temp.add(new Tuple<>(e, dist));
                    }
                    return returnResult;
                }).min((e1, e2) -> {
                    double d1 = e1.getDistanceToEntity(mc.thePlayer);
                    double d2 = e2.getDistanceToEntity(mc.thePlayer);
                    return Double.compare(d1, d2);
                });
        this.entities.clear();
        this.entities.addAll(temp);
        return opt;
    }

    private boolean getVacuum() {
        ItemStack currentItem = mc.thePlayer.getHeldItem();
        if (currentItem == null || !currentItem.getDisplayName().contains("Vacuum")) {
            int vacuum = InventoryUtils.getSlotIdOfItemInHotbar("Vacuum");
            if (vacuum == -1) {
                LogUtils.sendError("[Pests Destroyer On The Track] Failed to find vacuum in hotbar!");
                TaunahiConfig.pestsDestroyerOnTheTrack = false;
                stop();
                return true;
            }
            mc.thePlayer.inventory.currentItem = vacuum;
        }
        return false;
    }

    @Getter
    private final CopyOnWriteArrayList<Tuple<Entity, Double>> entities = new CopyOnWriteArrayList<>();
}
