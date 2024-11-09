package com.mozilla.mixin.client;

import com.mozilla.config.TaunahiConfig;
import com.mozilla.pathfinder.FlyPathFinderExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 2000)
public abstract class MixinEntity {
    @Shadow
    public float rotationYaw;

    @Shadow
    public abstract void moveFlying(float strafe, float forward, float friction);

    @Unique
    public boolean TaunahiV2$secondCall;

    @Inject(method = "moveFlying", at = @At("HEAD"), cancellable = true)
    private void moveFlyingHead(float strafe, float forward, float friction, CallbackInfo ci) {
        if (!FlyPathFinderExecutor.getInstance().isPathingOrDecelerating() || FlyPathFinderExecutor.getInstance().getNeededYaw() == Integer.MIN_VALUE)
            return;
        if ((Object) this != Minecraft.getMinecraft().thePlayer) return;
        if (TaunahiV2$secondCall) {
            TaunahiV2$secondCall = false;
            return;
        }
        float cachedYawM = rotationYaw;
        rotationYaw = !TaunahiConfig.flyPathfinderOringoCompatible ? FlyPathFinderExecutor.getInstance().getNeededYaw() : this.rotationYaw;
        TaunahiV2$secondCall = true;
        moveFlying(strafe, forward, friction);
        rotationYaw = cachedYawM;
        ci.cancel();
    }
}
