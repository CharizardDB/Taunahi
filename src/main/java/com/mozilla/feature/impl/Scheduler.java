package com.mozilla.feature.impl;

import cc.polyfrost.oneconfig.utils.Multithreading;
import com.mozilla.config.TaunahiConfig;
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
import com.mozilla.util.helper.Rotation;
import com.mozilla.util.helper.RotationConfiguration;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.TimeUnit;

public class Scheduler implements IFeature {

  private static Scheduler instance;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final RotationHandler rotation = RotationHandler.getInstance();
  @Getter
  private final Clock schedulerClock = new Clock();
  @Getter
  @Setter
  private SchedulerState schedulerState = SchedulerState.NONE;

  public static Scheduler getInstance() {
    if (instance == null) {
      instance = new Scheduler();
    }
    return instance;
  }

  @Override
  public String getName() {
    return "Scheduler";
  }

  @Override
  public boolean isRunning() {
    return schedulerClock.isScheduled();
  }

  @Override
  public boolean shouldPauseMacroExecution() {
    return false;
  }

  @Override
  public boolean shouldStartAtMacroStart() {
    return isToggled();
  }

  @Override
  public void start() {
    schedulerState = SchedulerState.FARMING;
    if (TaunahiConfig.schedulerResetOnDisable || schedulerClock.passed()) {
      schedulerClock.schedule(
          (long) (TaunahiConfig.schedulerFarmingTime * 60_000f + (Math.random() * TaunahiConfig.schedulerFarmingTimeRandomness * 60_000f)));
    } else {
      resume();
    }
    IFeature.super.start();
  }

  @Override
  public void stop() {
    // Just reset timer if macro was disabled during break
    if (TaunahiConfig.schedulerResetOnDisable || schedulerState == SchedulerState.BREAK) {
      schedulerState = SchedulerState.NONE;
      schedulerClock.reset();
    } else {
      pause();
    }
    IFeature.super.stop();
  }

  @Override
  public void resetStatesAfterMacroDisabled() {
    GameStateHandler.getInstance().setWasInJacobContest(false);
  }

  @Override
  public boolean isToggled() {
    return TaunahiConfig.enableScheduler;
  }

  @Override
  public boolean shouldCheckForFailsafes() {
    return false;
  }

  public boolean isFarming() {
    return !TaunahiConfig.enableScheduler || schedulerState == SchedulerState.FARMING;
  }

  public String getStatusString() {
    if (TaunahiConfig.enableScheduler) {
      return (schedulerState == SchedulerState.FARMING ? (EnumChatFormatting.GREEN + "Farming") : (EnumChatFormatting.DARK_AQUA + "Break"))
          + EnumChatFormatting.RESET + " for " +
          EnumChatFormatting.GOLD + LogUtils.formatTime(Math.max(schedulerClock.getRemainingTime(), 0)) + EnumChatFormatting.RESET + (
          schedulerClock.isPaused() ? " (Paused)" : "");
    } else {
      return "Farming";
    }
  }

  public void pause() {
    LogUtils.sendDebug("[Scheduler] Pausing");
    schedulerClock.pause();
  }

  public void resume() {
    LogUtils.sendDebug("[Scheduler] Resuming");
    schedulerClock.resume();
  }

  public void farmingTime() {
    schedulerState = SchedulerState.FARMING;
    schedulerClock.schedule(
        (long) (TaunahiConfig.schedulerFarmingTime * 60_000f + (Math.random() * TaunahiConfig.schedulerFarmingTimeRandomness * 60_000f)));
    if (TaunahiConfig.schedulerDisconnectDuringBreak) {
      AutoReconnect.getInstance().start();
      pause();
    } else {
      if (mc.currentScreen != null) {
        PlayerUtils.closeScreen();
      }
      MacroHandler.getInstance().resumeMacro();
    }
  }

  public void breakTime() {
    KeyBindUtils.stopMovement();
    MacroHandler.getInstance().pauseMacro(true);
    schedulerState = SchedulerState.BREAK;
    schedulerClock.schedule(
        (long) ((TaunahiConfig.schedulerBreakTime * 60_000f) + (Math.random() * TaunahiConfig.schedulerBreakTimeRandomness * 60_000f)));
    LogUtils.sendDebug(
        "BreakTime: " + TaunahiConfig.schedulerBreakTime + ", BreakTimeRandomness: " + TaunahiConfig.schedulerBreakTimeRandomness + ", Left: "
            + schedulerClock.getRemainingTime());

    if (TaunahiConfig.schedulerDisconnectDuringBreak) {
      Multithreading.schedule(() -> {
        mc.getNetHandler().getNetworkManager()
            .closeChannel(new ChatComponentText("Will reconnect after break ends! Restart if you don't see a break timer."));
      }, 1000, TimeUnit.MILLISECONDS);
    } else {
      Multithreading.schedule(() -> {
        long randomTime4 = TaunahiConfig.getRandomRotationTime();
        this.rotation.easeTo(
            new RotationConfiguration(
                new Rotation(
                    (float) (mc.thePlayer.rotationYaw + Math.random() * 20 - 10),
                    (float) Math.min(90, Math.max(-90, (mc.thePlayer.rotationPitch + Math.random() * 30 - 15)))
                ),
                randomTime4, null
            )
        );
        if (TaunahiConfig.openInventoryOnSchedulerBreaks) {
          Multithreading.schedule(InventoryUtils::openInventory, randomTime4 + 350, TimeUnit.MILLISECONDS);
        }
      }, (long) (300 + Math.random() * 200), TimeUnit.MILLISECONDS);
    }
  }

  @SubscribeEvent
  public void onTick(TickEvent.ClientTickEvent event) {
    if (!TaunahiConfig.enableScheduler || event.phase == TickEvent.Phase.END || !MacroHandler.getInstance().getCurrentMacro().isPresent()) {
      return;
    }
    if (FeatureManager.getInstance().isAnyOtherFeatureEnabled(this)) {
      return;
    }

    // Should Never Trigger While Disconnected
    if (TaunahiConfig.pauseSchedulerDuringJacobsContest && GameStateHandler.getInstance().inJacobContest() && !GameStateHandler.getInstance()
        .isWasInJacobContest()) {
      LogUtils.sendDebug("[Scheduler] Jacob contest started, pausing scheduler");
      schedulerClock.pause();
      if (schedulerState == SchedulerState.BREAK) {
        if (mc.currentScreen != null) {
          PlayerUtils.closeScreen();
        }
        MacroHandler.getInstance().resumeMacro();
        pause();
      }
      GameStateHandler.getInstance().setWasInJacobContest(true);
      return;
    } else if (TaunahiConfig.pauseSchedulerDuringJacobsContest && GameStateHandler.getInstance().isWasInJacobContest()
        && !GameStateHandler.getInstance().inJacobContest()) {
      LogUtils.sendDebug("[Scheduler] Jacob contest ended, resuming scheduler");
      schedulerClock.resume();
      if (schedulerState == SchedulerState.BREAK) {
        MacroHandler.getInstance().pauseMacro();
      }
      GameStateHandler.getInstance().setWasInJacobContest(false);
      return;
    }

    if (MacroHandler.getInstance().isMacroToggled()
        && !schedulerClock.isPaused() && schedulerClock.passed()
        && !AutoReconnect.getInstance().isRunning()) {
      if (MacroHandler.getInstance().isCurrentMacroEnabled()
          && schedulerState == SchedulerState.FARMING
          && (!TaunahiConfig.schedulerWaitUntilRewarp || PlayerUtils.isStandingOnRewarpLocation())) {
        breakTime();
      } else if (schedulerState == SchedulerState.BREAK) {
        farmingTime();
      }
    }
  }


  @SubscribeEvent
  public void onKeyPress(GuiScreenEvent.KeyboardInputEvent event) {
    if (!isRunning()) {
      return;
    }
    if (!(mc.currentScreen instanceof GuiDisconnected)) {
      return;
    }

    if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
      if (MacroHandler.getInstance().isMacroToggled()) {
        MacroHandler.getInstance().disableMacro();
      }
      mc.displayGuiScreen(new GuiMainMenu());
    }
    if (TaunahiConfig.toggleMacro.getKeyBinds().contains(Keyboard.getEventKey())) {
      farmingTime();
    }
  }

  public enum SchedulerState {
    NONE,
    FARMING,
    BREAK
  }
}
