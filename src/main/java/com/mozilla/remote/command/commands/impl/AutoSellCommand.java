package com.mozilla.remote.command.commands.impl;

import com.google.gson.JsonObject;
import com.mozilla.feature.impl.AutoSell;
import com.mozilla.handler.GameStateHandler;
import com.mozilla.remote.command.commands.ClientCommand;
import com.mozilla.remote.command.commands.Command;
import com.mozilla.remote.struct.RemoteMessage;

@Command(label = "autosell")
public class AutoSellCommand extends ClientCommand {

    @Override
    public void execute(RemoteMessage message) {
        JsonObject data = new JsonObject();
        data.addProperty("username", mc.getSession().getUsername());
        data.addProperty("uuid", mc.getSession().getPlayerID());
        data.addProperty("toggled", !AutoSell.getInstance().isEnabled());

        if (GameStateHandler.getInstance().getCookieBuffState() != GameStateHandler.BuffState.ACTIVE)
            data.addProperty("info", "You don't have cookie buff active!");
        else if (GameStateHandler.getInstance().getServerClosingSeconds().isPresent())
            data.addProperty("info", "Server is closing in " + GameStateHandler.getInstance().getServerClosingSeconds().get() + " seconds!");
        else if (GameStateHandler.getInstance().getLocation() != GameStateHandler.Location.GARDEN)
            data.addProperty("info", "You are not in the garden!");
        else {
            AutoSell.getInstance().enable(true);
            data.addProperty("info", "AutoSell enabled");
        }

        RemoteMessage response = new RemoteMessage(label, data);
        send(response);
    }
}