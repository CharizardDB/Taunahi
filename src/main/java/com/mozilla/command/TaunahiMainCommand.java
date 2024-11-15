package com.mozilla.command;

import cc.polyfrost.oneconfig.utils.commands.annotations.Command;
import cc.polyfrost.oneconfig.utils.commands.annotations.Description;
import cc.polyfrost.oneconfig.utils.commands.annotations.Main;
import cc.polyfrost.oneconfig.utils.commands.annotations.SubCommand;
import com.mozilla.BufferedCipher;
import com.mozilla.config.TaunahiConfig;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.pathfinder.FlyPathFinderExecutor;
import com.mozilla.util.LogUtils;
import com.mozilla.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

import java.util.Optional;

@Command(value = "th", aliases = {"taunahi"}, description = "Taunahi main command")
public class TaunahiMainCommand {

    @Main
    public void mainCommand() {
        BufferedCipher.config.openGui();
    }

    @SubCommand(aliases = {"pfm"})
    public void pathfindmob(
            @Description("Name of a mob, to pathfind to, for example 'Zombie', 'Slime' etc") String mobName,
            @Description(value = "Tell the pathfinder, to constantly follow and recalibrate path until arrive", autoCompletesTo = {"true", "false"}) boolean follow,
            @Description(value = "Tell the pathfinder, to smooth out the path", autoCompletesTo = {"true", "false"}) boolean smooth) {
        Optional<Entity> entity = Minecraft.getMinecraft().theWorld.loadedEntityList.stream().filter(e -> e.getName().toLowerCase().contains(mobName.toLowerCase())).findFirst();
        if (!entity.isPresent()) {
            LogUtils.sendError("[Pathfinder] Could not find entity with name: " + mobName);
            return;
        }
        FlyPathFinderExecutor.getInstance().findPath(entity.get(), follow, smooth);
    }

    @SubCommand(aliases = {"pfm"})
    public void pathfindmob(
            @Description("Name of a mob, to pathfind to, for example 'Zombie', 'Slime' etc") String mobName,
            @Description(value = "Tell the pathfinder, to constantly follow and recalibrate path until arrive", autoCompletesTo = {"true", "false"}) boolean follow,
            @Description(value = "Tell the pathfinder, to smooth out the path", autoCompletesTo = {"true", "false"}) boolean smooth,
            @Description(value = "Y modifier") float yModifier) {
        Optional<Entity> entity = Minecraft.getMinecraft().theWorld.loadedEntityList.stream().filter(e -> e.getName().toLowerCase().contains(mobName.toLowerCase())).findFirst();
        if (!entity.isPresent()) {
            LogUtils.sendError("[Pathfinder] Could not find entity with name: " + mobName);
            return;
        }
        FlyPathFinderExecutor.getInstance().findPath(entity.get(), follow, smooth, yModifier, false);
    }

    @SubCommand(aliases = {"pf"})
    public void pathfind(int x, int y, int z,
                         @Description(value = "Tell the pathfinder, to constantly follow and recalibrate path until arrive", autoCompletesTo = {"true", "false"}) boolean follow,
                         @Description(value = "Tell the pathfinder, to smooth out the path", autoCompletesTo = {"true", "false"}) boolean smooth) {
        FlyPathFinderExecutor.getInstance().findPath(new Vec3(x, y, z), follow, smooth);
    }

    @SubCommand(aliases = {"sp"})
    public void stoppath() {
        FlyPathFinderExecutor.getInstance().stop();
    }

    @SubCommand(aliases = {"up"})
    public void update() {
        PlayerUtils.closeScreen();
        TaunahiConfig.checkForUpdate();
    }

    @SubCommand
    public void test(){
        LogUtils.sendSuccess("SprayonatorState: " + GameStateHandler.getInstance().getSprayonatorState());
    }
}
