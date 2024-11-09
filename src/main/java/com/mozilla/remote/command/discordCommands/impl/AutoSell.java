package com.mozilla.remote.command.discordCommands.impl;

import com.mozilla.remote.WebsocketHandler;
import com.mozilla.remote.command.discordCommands.DiscordCommand;
import com.mozilla.remote.command.discordCommands.Option;
import com.mozilla.remote.waiter.Waiter;
import com.mozilla.remote.waiter.WaiterHandler;
import com.mozilla.util.AvatarUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.Objects;

public class AutoSell extends DiscordCommand {
    public static final String name = "autosell";
    public static final String description = "Trigger the AutoSell feature";

    public AutoSell() {
        super(AutoSell.name, AutoSell.description);
        Option ign = new Option(OptionType.STRING, "ign", "The IGN of the player you want to toggle this account", false, true);
        addOptions(ign);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        WaiterHandler.register(new Waiter(
                5000,
                name,
                action -> {
                    String username = action.args.get("username").getAsString();
                    String uuid = action.args.get("uuid").getAsString();
                    boolean toggled = action.args.get("toggled").getAsBoolean();
                    boolean error = action.args.has("info");

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.addField("Username", username, false);
                    if (error) {
                        embedBuilder.addField("Info", action.args.get("info").getAsString(), false);
                    }
                    embedBuilder.addField("Turned AutoSell", (toggled && !error) ? "On" : "Off", false);
                    int random = (int) (Math.random() * 0xFFFFFF);
                    embedBuilder.setColor(random);
                    embedBuilder.setFooter("-> Taunahi Remote Control", "");
                    String avatar = AvatarUtils.getAvatarUrl(uuid);
                    embedBuilder.setAuthor("Instance name -> " + username, avatar, avatar);

                    try {
                        event.getHook().sendMessageEmbeds(embedBuilder.build()).queue();
                    } catch (Exception e) {
                        event.getChannel().sendMessageEmbeds(embedBuilder.build()).queue();
                    }
                },
                timeoutAction -> event.getHook().sendMessage("Can't toggle the bot").queue(),
                event
        ));

        if (event.getOption("ign") != null) {
            String ign = Objects.requireNonNull(event.getOption("ign")).getAsString();
            if (WebsocketHandler.getInstance().getWebsocketServer().minecraftInstances.containsValue(ign)) {
                WebsocketHandler.getInstance().getWebsocketServer().minecraftInstances.forEach((webSocket, s) -> {
                    if (s.equals(ign)) {
                        sendMessage(webSocket);
                    }
                });
            } else {
                event.getHook().sendMessage("There isn't any instances connected with that IGN").queue();
            }
        } else {
            WebsocketHandler.getInstance().getWebsocketServer().minecraftInstances.forEach((webSocket, s) -> sendMessage(webSocket));
        }
    }
}
