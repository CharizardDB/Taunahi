package com.mozilla.mixin.client;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.handler.MacroHandler;
import com.mozilla.util.helper.AudioManager;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.audio.SoundPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class MixinSoundManager {
    @Inject(method = "getNormalizedVolume", at = @At("RETURN"), cancellable = true)
    private void getNormalizedVolume(ISound sound, SoundPoolEntry entry, SoundCategory category, CallbackInfoReturnable<Float> cir) {
        if (MacroHandler.getInstance().isMacroToggled() && TaunahiConfig.muteTheGame && !AudioManager.getInstance().isSoundPlaying() && !FailsafeManager.getInstance().getChooseEmergencyDelay().isScheduled() && !FailsafeManager.getInstance().triggeredFailsafe.isPresent()) {
            cir.setReturnValue(0f);
        }
    }
}
