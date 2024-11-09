package com.mozilla.mixin.gui;

import com.mozilla.BufferedCipher;
import com.mozilla.config.TaunahiConfig;
import com.mozilla.gui.AutoUpdaterGUI;
import com.mozilla.gui.WelcomeGUI;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu {
    @Shadow
    private String splashText;

    @Final
    @Inject(method = "updateScreen", at = @At("RETURN"))
    private void initGui(CallbackInfo ci) {
        if (BufferedCipher.isDebug) {
            this.splashText = "Fix Taunahi <3";
            return;
        }
        if (!TaunahiConfig.shownWelcomeGUI) {
            WelcomeGUI.showGUI();
        }
        if (!AutoUpdaterGUI.checkedForUpdates) {
            AutoUpdaterGUI.checkedForUpdates = true;
            AutoUpdaterGUI.getLatestVersion();
            if (AutoUpdaterGUI.isOutdated) {
                AutoUpdaterGUI.showGUI();
            }
        }
        if (AutoUpdaterGUI.isOutdated && !TaunahiConfig.streamerMode)
            this.splashText = "Update Taunahi <3";
    }
}