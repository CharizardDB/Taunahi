package com.mozilla.mixin.client;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.feature.impl.BanInfoWS;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = Integer.MAX_VALUE)
public class MixinMinecraft {

    @Shadow
    public GuiScreen currentScreen;

    @Shadow
    public GameSettings gameSettings;

    @Shadow
    public boolean inGameHasFocus;

    @Shadow
    public MovingObjectPosition objectMouseOver;

    @Shadow
    private Entity renderViewEntity;

    @Shadow
    public PlayerControllerMP playerController;

    @Shadow
    public WorldClient theWorld;

    @Shadow
    public EntityPlayerSP thePlayer;

    @Inject(method = "sendClickBlockToController", at = @At("RETURN"))
    private void sendClickBlockToController(CallbackInfo ci) {
        if (!TaunahiConfig.fastBreak || !(MacroHandler.getInstance().getCurrentMacro().isPresent() && MacroHandler.getInstance().isMacroToggled())) {
            return;
        }
        if (TaunahiConfig.disableFastBreakDuringBanWave && BanInfoWS.getInstance().isBanwave()) {
            return;
        }
        if (TaunahiConfig.disableFastBreakDuringJacobsContest && GameStateHandler.getInstance().inJacobContest()) {
            return;
        }

        boolean shouldClick = this.currentScreen == null && this.gameSettings.keyBindAttack.isKeyDown() && this.inGameHasFocus;
        if (this.objectMouseOver != null && shouldClick) {
            boolean isCactus = false;

            for (int i = 0; i < TaunahiConfig.fastBreakSpeed + 1; i++) {
//          try catch when player break block and the block is not exist(ghost block?) or some player in front of the block
                try {
                    if (TaunahiConfig.fastBreakRandomization && (Math.random() * 100 < (100 - TaunahiConfig.fastBreakRandomizationChance))) {
                        break;
                    }

                    BlockPos clickedBlock = this.objectMouseOver.getBlockPos();
                    Block block = this.theWorld.getBlockState(clickedBlock).getBlock();
                    this.objectMouseOver = this.renderViewEntity.rayTrace(this.playerController.getBlockReachDistance(), 1.0F);

                    if (block == Blocks.cactus) {
                        isCactus = true;
                    } else {
                        isCactus = false;
                    }

                    if (this.objectMouseOver == null || this.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                        break;
                    }

                    BlockPos newBlock = this.objectMouseOver.getBlockPos();
                    Block blockTryBreak = this.theWorld.getBlockState(newBlock).getBlock();

                    if (this.theWorld.getBlockState(newBlock).getBlock().getPlayerRelativeBlockHardness(this.thePlayer, this.theWorld, clickedBlock) < 1.0F) {
                        return;
                    }

                    if (isCactus) {
                        this.playerController.resetBlockRemoving();
                    }

                    if (newBlock == null || this.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || newBlock.equals(clickedBlock) || blockTryBreak.getMaterial() == Material.air) {
                        break;
                    }

                    this.thePlayer.swingItem();
                    this.playerController.clickBlock(newBlock, this.objectMouseOver.sideHit);
                } catch (Exception ignored) {

                }
            }

        }
    }
}