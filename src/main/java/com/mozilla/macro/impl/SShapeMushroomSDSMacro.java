package com.mozilla.macro.impl;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.macro.AbstractMacro;
import com.mozilla.util.AngleUtils;
import com.mozilla.util.BlockUtils;
import com.mozilla.util.KeyBindUtils;
import com.mozilla.util.LogUtils;
import com.mozilla.util.helper.Rotation;
import com.mozilla.util.helper.RotationConfiguration;

import java.util.Optional;

public class SShapeMushroomSDSMacro extends AbstractMacro {

    @Override
    public void onEnable() {
        super.onEnable();

        if (!isPitchSet()) {
            setPitch((float) (6.5f + Math.random() * 1f)); // 6.5 - 7.5
        }

        if (!isYawSet()) {
            setYaw(AngleUtils.getClosest());
            setClosest90Deg(Optional.of(AngleUtils.getClosest(getYaw())));
        }

        if (MacroHandler.getInstance().isTeleporting()) return;
        setRestoredState(false);
        Rotation newRotation = new Rotation((float) (getClosest90Deg().orElse(AngleUtils.getClosest()) + -16 + (Math.random() * 1 - 0.5)), getPitch());
        setYaw(newRotation.getYaw());
        if (TaunahiConfig.dontFixAfterWarping && Math.abs(getYaw() - AngleUtils.get360RotationYaw()) < 0.1) return;
        getRotation().easeTo(
                new RotationConfiguration(
                        newRotation,
                        TaunahiConfig.getRandomRotationTime(), null
                ).easeOutBack(!MacroHandler.getInstance().isResume())
        );

    }

    @Override
    public void actionAfterTeleport() {
    }

    @Override
    public void updateState() {
        if (getCurrentState() == null)
            changeState(State.NONE);

        switch (getCurrentState()) {
            case LEFT: {
                if (GameStateHandler.getInstance().isBackWalkable() && !TaunahiConfig.alwaysHoldW) {
                    changeState(State.SWITCHING_LANE);
                } else {
                    if (GameStateHandler.getInstance().isLeftWalkable()) {
                        changeState(State.LEFT);
                    } else {
                        changeState(State.NONE);
                    }
                }
                break;
            }
            case RIGHT: {
                if (GameStateHandler.getInstance().isBackWalkable() && !TaunahiConfig.alwaysHoldW) {
                    changeState(State.SWITCHING_LANE);
                } else {
                    if (GameStateHandler.getInstance().isRightWalkable()) {
                        changeState(State.RIGHT);
                    } else {
                        changeState(State.NONE);
                    }
                }
                break;
            }
            case DROPPING: {
                if (mc.thePlayer.onGround) {
                    changeState(State.NONE);
                } else {
                    GameStateHandler.getInstance().scheduleNotMoving();
                }
                break;
            }
            case NONE: {
                changeState(calculateDirection());
                break;
            }
            default: {
                LogUtils.sendDebug("This shouldn't happen, but it did...");
                changeState(State.NONE);
            }
        }
    }

    @Override
    public void invokeState() {
        if (getCurrentState() == null) return;
        switch (getCurrentState()) {
            case RIGHT:
                KeyBindUtils.holdThese(
                        mc.gameSettings.keyBindRight,
                        mc.gameSettings.keyBindAttack
                );
                break;
            case LEFT: {
                KeyBindUtils.holdThese(
                        mc.gameSettings.keyBindLeft,
                        mc.gameSettings.keyBindAttack
                );
                break;
            }
            case SWITCHING_LANE: {
                if (!BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(0, 0, -1, getClosest90Deg().orElse(AngleUtils.getClosest())))) {
                    changeState(State.NONE);
                    KeyBindUtils.stopMovement();
                } else {
                    KeyBindUtils.holdThese(
                            mc.gameSettings.keyBindBack,
                            mc.gameSettings.keyBindAttack
                    );
                }
                break;
            }
        }
    }

    @Override
    public State calculateDirection() {
        if (BlockUtils.rightCropIsReady()) {
            return State.RIGHT;
        } else if (BlockUtils.leftCropIsReady()) {
            return State.LEFT;
        }

        for (int i = 1; i < 180; i++) {
            if (!BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(i, 0, 0, getClosest90Deg().orElse(AngleUtils.getClosest())))) {
                return State.LEFT;
            }
            if (!BlockUtils.canWalkThrough(BlockUtils.getRelativeBlockPos(-i, 0, 0, getClosest90Deg().orElse(AngleUtils.getClosest()))))
                return State.RIGHT;
        }

        LogUtils.sendDebug("No direction found");
        return State.NONE;
    }
}
