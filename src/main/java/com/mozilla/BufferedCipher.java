package com.mozilla;

import baritone.api.BaritoneAPI;
import cc.polyfrost.oneconfig.utils.commands.CommandManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mozilla.command.TaunahiMainCommand;
import com.mozilla.command.RewarpCommand;
import com.mozilla.config.TaunahiConfig;
import com.mozilla.event.MillisecondEvent;
import com.mozilla.failsafe.FailsafeManager;
import com.mozilla.feature.FeatureManager;
import com.mozilla.feature.impl.BanInfoWS;
import com.mozilla.feature.impl.MovRecPlayer;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.handler.MacroHandler;
import com.mozilla.handler.RotationHandler;
import com.mozilla.pathfinder.FlyPathFinderExecutor;
import com.mozilla.remote.DiscordBotHandler;
import com.mozilla.remote.WebsocketHandler;
import com.mozilla.util.*;
import com.mozilla.util.helper.AudioManager;
import com.mozilla.util.helper.BaritoneEventListener;
import com.mozilla.util.helper.TickTask;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(modid = "Taunahiv2", useMetadata = true)
public class BufferedCipher {
    public static final String VERSION = "%%VERSION%%";
    public static final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
    public static TaunahiConfig config;
    public static boolean sentInfoAboutShittyClient = false;
    public static boolean isDebug = false;
    private final Minecraft mc = Minecraft.getMinecraft();
    public static File jarFile = null;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        jarFile = event.getSourceFile();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        initializeFields();
        initializeListeners();
        initializeCommands();
        FeatureManager.getInstance().fillFeatures().forEach(MinecraftForge.EVENT_BUS::register);

        mc.gameSettings.pauseOnLostFocus = false;
        mc.gameSettings.gammaSetting = 1000;
        BanInfoWS.getInstance().loadStatsOnInit();
        isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("-agentlib:jdwp");
        if (!TaunahiConfig.streamerMode && TaunahiConfig.changeWindowTitle)
            Display.setTitle("Taunahi 〔v" + VERSION + "〕 " + (!isDebug ? "Bing Chilling" : "wazzup alex?") + " ☛ " + Minecraft.getMinecraft().getSession().getUsername());
        FailsafeUtils.getInstance();
        PlotUtils.init();

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(() -> MinecraftForge.EVENT_BUS.post(new MillisecondEvent()), 0, 1, TimeUnit.MILLISECONDS);
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().registerEventListener(new BaritoneEventListener());
    }

    @SubscribeEvent
    public void onTickSendInfoAboutShittyClient(TickEvent.PlayerTickEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (sentInfoAboutShittyClient) return;

        if (ReflectionUtils.hasPackageInstalled("feather")) {
            LogUtils.sendNotification("Taunahi", "You've got Feather Client installed! Be aware, you might have a lot of bugs because of this shitty client!", 15000);
            LogUtils.sendError("You've got §6§lFeather Client §cinstalled! Be aware, you might have a lot of bugs because of this shitty client!");
        }
        if (ReflectionUtils.hasPackageInstalled("cc.woverflow.hytils.HytilsReborn")) {
            LogUtils.sendNotification("Taunahi", "You've got Hytils installed in your mods folder! This will cause many issues with rewarping as it sends tons of commands every minute.", 15000);
            LogUtils.sendError("You've got §6§lHytils §cinstalled in your mods folder! This will cause many issues with rewarping as it sends tons of commands every minute.");
        }
        if (ReflectionUtils.hasPackageInstalled("com.tterrag.blur")) {
            LogUtils.sendNotification("Taunahi", "You've got BlurMC installed in your mods folder! This will break AutoSell, Pests Destroyer and other features that need to work with inventories!", 15000);
            LogUtils.sendError("You've got §6§lBlurMC §cinstalled in your mods folder! This will break AutoSell, Pests Destroyer and other features that need to work with inventories!");
        }
        if (ReflectionUtils.hasPackageInstalled("at.hannibal2.skyhanni")) {
            try {
                // Get the ConfigManager instance
                Class<?> skyHanniModClass = Class.forName("at.hannibal2.skyhanni.SkyHanniMod");
                Field configManagerField = skyHanniModClass.getDeclaredField("configManager");
                configManagerField.setAccessible(true);
                Object configManager = configManagerField.get(null); // Assuming it's a static field

                // Get the Features instance
                Method getFeaturesMethod = configManager.getClass().getMethod("getFeatures");
                Object featuresInstance = getFeaturesMethod.invoke(configManager);

                // Get the garden field from Features
                Class<?> featuresClass = Class.forName("at.hannibal2.skyhanni.config.Features");
                Field gardenField = featuresClass.getDeclaredField("garden");
                gardenField.setAccessible(true);

                // Get the GardenConfig instance
                Object gardenConfigInstance = gardenField.get(featuresInstance);

                // Get the pests field from GardenConfig
                Class<?> gardenConfigClass = Class.forName("at.hannibal2.skyhanni.config.features.garden.GardenConfig");
                Field pestsField = gardenConfigClass.getDeclaredField("pests");
                pestsField.setAccessible(true);

                // Get the PestsConfig instance
                Object pestsConfigInstance = pestsField.get(gardenConfigInstance);

                // Get the pestWaypoint field from PestsConfig
                Class<?> pestsConfigClass = Class.forName("at.hannibal2.skyhanni.config.features.garden.pests.PestsConfig");
                Field pestWaypointField = pestsConfigClass.getDeclaredField("pestWaypoint");
                pestWaypointField.setAccessible(true);

                // Get the PestWaypointConfig instance
                Object pestWaypointInstance = pestWaypointField.get(pestsConfigInstance);

                // Now we can access the hideParticles field
                Class<?> pestWaypointClass = Class.forName("at.hannibal2.skyhanni.config.features.garden.pests.PestWaypointConfig");
                Field hideParticlesField = pestWaypointClass.getDeclaredField("hideParticles");
                hideParticlesField.setAccessible(true);

                if (hideParticlesField.getBoolean(pestWaypointInstance)) {
                    LogUtils.sendWarning("Disabling SkyHanni Pest Waypoint 'Hide Particles' option. This is required for Pests Destroyer to work properly.");
                    hideParticlesField.setBoolean(pestWaypointInstance, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (Minecraft.isRunningOnMac && TaunahiConfig.autoUngrabMouse) {
            TaunahiConfig.autoUngrabMouse = false;
            LogUtils.sendNotification("Taunahi", "Auto Ungrab Mouse feature doesn't work properly on Mac OS. It has been disabled automatically.", 15000);
            LogUtils.sendError("Auto Ungrab Mouse feature doesn't work properly on Mac OS. It has been disabled automatically.");
        }
        if (TaunahiConfig.configVersion < 3) {
            TaunahiConfig.visitorsMacroMaxSpendLimit = 0.7f;
            LogUtils.sendNotification("Taunahi", "'Max Spend Limit' in Visitors Macro settings has been set to 0.7 automatically, because of change of type. Make sure to update it to your preferences", 15000);
            LogUtils.sendWarning("'Max Spend Limit' in Visitors Macro settings has been set to 0.7 automatically, because of change of type. Make sure to update it to your preferences");
        }
        if (!TaunahiConfig.flyPathfinderOringoCompatible && ReflectionUtils.hasModFile("oringo")) {
            TaunahiConfig.flyPathfinderOringoCompatible = true;
            LogUtils.sendNotification("Taunahi", "You've got Oringo installed in your mods folder! Taunahi will use Oringo compatibility mode for FlyPathfinder.", 15000);
            LogUtils.sendWarning("You've got §6§lOringo §cinstalled in your mods folder! Taunahi will use Oringo compatibility mode for FlyPathfinder.");
        }
        if (TaunahiConfig.configVersion == 3 && TaunahiConfig.macroType > 7) {
            TaunahiConfig.macroType += 1; // Added cocoa bean macro with trapdoors
        }
        if (TaunahiConfig.configVersion <= 5) {
            //noinspection deprecation
            if (TaunahiConfig.visitorsFilteringMethod) {
                TaunahiConfig.filterVisitorsByName = true;
                TaunahiConfig.filterVisitorsByRarity = false;
            } else {
                TaunahiConfig.filterVisitorsByRarity = true;
                TaunahiConfig.filterVisitorsByName = false;
            }
        }
        if (TaunahiConfig.configVersion != 6)
            TaunahiConfig.configVersion = 6;
        sentInfoAboutShittyClient = true;
    }

    private void initializeListeners() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(FailsafeManager.getInstance());
        MinecraftForge.EVENT_BUS.register(GameStateHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(MacroHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(TickTask.getInstance());
        MinecraftForge.EVENT_BUS.register(MovRecPlayer.getInstance());
        MinecraftForge.EVENT_BUS.register(WebsocketHandler.getInstance());
        if (Loader.isModLoaded("Taunahijdadependency") && checkIfJDAVersionCorrect())
            MinecraftForge.EVENT_BUS.register(DiscordBotHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(AudioManager.getInstance());
        MinecraftForge.EVENT_BUS.register(RotationHandler.getInstance());
        MinecraftForge.EVENT_BUS.register(FlyPathFinderExecutor.getInstance());
        MinecraftForge.EVENT_BUS.register(new TablistUtils());
        MinecraftForge.EVENT_BUS.register(new ScoreboardUtils());
    }

    private void initializeFields() {
        config = new TaunahiConfig();
    }

    private void initializeCommands() {
        ClientCommandHandler.instance.registerCommand(new RewarpCommand());
        CommandManager.register(new TaunahiMainCommand());
    }

    public static boolean isJDAVersionCorrect = false;

    public static boolean checkIfJDAVersionCorrect() {
        Optional<ModContainer> modContainer = Loader.instance().getActiveModList().stream()
                .filter(mod -> "Taunahijdadependency".equals(mod.getModId()))
                .findFirst();
        System.out.println("JDA Version: " + modContainer.map(ModContainer::getVersion).orElse("null"));
        isJDAVersionCorrect = modContainer.map(container -> container.getVersion().equals("1.0.4")).orElse(false);
        return isJDAVersionCorrect;
    }
}
