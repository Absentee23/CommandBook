/*
 * CommandBook
 * Copyright (C) 2011 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.commandbook;

import com.zachsthings.libcomponents.spout.SpoutComponent;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.config.ConfigurationBase;
import com.zachsthings.libcomponents.config.Setting;
import com.sk89q.commandbook.events.OnlineListSendEvent;
import org.spout.api.ChatColor;
import org.spout.api.command.CommandContext;
import org.spout.api.command.CommandSource;
import org.spout.api.command.annotated.Command;
import org.spout.api.command.annotated.CommandPermissions;
import org.spout.api.event.EventHandler;
import org.spout.api.event.Listener;
import org.spout.api.event.Order;
import org.spout.api.event.player.PlayerJoinEvent;
import org.spout.api.exception.CommandException;
import org.spout.api.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ComponentInformation(friendlyName = "Online List", desc = "Lists online players both on command and on player join.")
public class OnlineListComponent extends SpoutComponent implements Listener {

    private LocalConfiguration config;

    @Override
    public void enable() {
        config = configure(new LocalConfiguration());
        CommandBook.game().getEventManager().registerEvents(this, this);
        registerCommands(Commands.class);
    }

    @Override
    public void reload() {
        super.reload();
        configure(config);
    }

    private static class LocalConfiguration extends ConfigurationBase {
        @Setting("show-max-players") public boolean playersListMaxPlayers = true;
        @Setting("grouped-names") public boolean playersListGroupedNames;
        @Setting("colored-names") public boolean playersListColoredNames;
        @Setting("list-on-join") public boolean listOnJoin = true;
    }

    /**
     * Send the online player list.
     *
     * @param online
     * @param sender
     */
    public void sendOnlineList(Player[] online, CommandSource sender) {

        StringBuilder out = new StringBuilder();

        // This applies mostly to the console, so there might be 0 players
        // online if that's the case!
        if (online.length == 0) {
            sender.sendMessage("0 players are online.");
            return;
        }

        out.append(ChatColor.GRAY + "Online (");
        out.append(online.length);
        if (config.playersListMaxPlayers) {
            out.append("/");
            out.append(CommandBook.game().getMaxPlayers());
        }
        out.append("): ");
        out.append(ChatColor.WHITE);

        if (config.playersListGroupedNames) {
            Map<String, List<Player>> groups = new HashMap<String, List<Player>>();

            for (Player player : online) {
                String[] playerGroups = player.getGroups();
                String group = playerGroups.length > 0 ? playerGroups[0] : "Default";

                if (groups.containsKey(group)) {
                    groups.get(group).add(player);
                } else {
                    List<Player> list = new ArrayList<Player>();
                    list.add(player);
                    groups.put(group, list);
                }
            }

            for (Map.Entry<String, List<Player>> entry : groups.entrySet()) {
                out.append("\n");
                out.append(ChatColor.WHITE).append(entry.getKey());
                out.append(": ");

                // To keep track of commas
                boolean first = true;

                for (Player player : entry.getValue()) {
                    if (!first) {
                        out.append(", ");
                    }

                    if (config.playersListColoredNames) {
                        out.append(player.getDisplayName()).append(ChatColor.WHITE);
                    } else {
                        out.append(player.getName());
                    }

                    first = false;
                }
            }

        } else {
            // To keep track of commas
            boolean first = true;

            for (Player player : online) {
                if (!first) {
                    out.append(", ");
                }

                if (config.playersListColoredNames) {
                    out.append(player.getDisplayName()).append(ChatColor.WHITE);
                } else {
                    out.append(player.getName());
                }

                first = false;
            }
        }

        String[] lines = out.toString().split("\n");

        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    @EventHandler(order = Order.LATE)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.listOnJoin) return;
        Player player = event.getPlayer();
        CommandBook.callEvent(new OnlineListSendEvent(player));

        sendOnlineList(
                CommandBook.game().getOnlinePlayers(), player);
    }

    public class Commands {
        @Command(aliases = {"who", "list", "playerlist", "online", "players"},
                usage = "[filter]", desc = "Get the list of online users",
                min = 0, max = 1)
        @CommandPermissions({"commandbook.who"})
        public void who(CommandContext args, CommandSource sender) throws CommandException {
            Player[] online = CommandBook.game().getOnlinePlayers();

            // Some crappy wrappers uses this to detect if the server is still
            // running, even though this is a very unreliable way to do it
            if (!(sender instanceof Player) && CommandBook.inst().crappyWrapperCompat) {
                StringBuilder out = new StringBuilder();

                out.append("Connected players: ");

                // To keep track of commas
                boolean first = true;

                // Now go through the list of players and find any matching players
                // (in case of a filter), and create the list of players.
                for (Player player : online) {
                    if (!first) {
                        out.append(", ");
                    }

                    out.append(CommandBook.inst().useDisplayNames ? player.getDisplayName() : player.getName());
                    out.append(ChatColor.WHITE);

                    first = false;
                }

                sender.sendMessage(out.toString());

                return;
            }

            CommandBook.callEvent(new OnlineListSendEvent(sender));

            // This applies mostly to the console, so there might be 0 players
            // online if that's the case!
            if (online.length == 0) {
                sender.sendMessage("0 players are online.");
                return;
            }

            // Get filter
            String filter = args.getString(0, "").toLowerCase();
            filter = filter.length() == 0 ? null : filter;

            // For filtered queries, we say something a bit different
            if (filter == null) {
                sendOnlineList(
                        CommandBook.game().getOnlinePlayers(), sender);
                return;

            }

            StringBuilder out = new StringBuilder();

            out.append(ChatColor.GRAY + "Found players (out of ");
            out.append(ChatColor.GRAY + "" + online.length);
            out.append(ChatColor.GRAY + "): ");
            out.append(ChatColor.WHITE);

            // To keep track of commas
            boolean first = true;

            // Now go through the list of players and find any matching players
            // (in case of a filter), and create the list of players.
            for (Player player : online) {
                // Process the filter
                if (!player.getName().toLowerCase().contains(filter)) {
                    break;
                }

                if (!first) {
                    out.append(", ");
                }

                out.append(player.getName());

                first = false;
            }

            // This means that no matches were found!
            if (first) {
                sender.sendMessage(ChatColor.RED + "No players (out of "
                        + online.length + ") matched '" + filter + "'.");
                return;
            }

            sender.sendMessage(out.toString());
        }
    }
}
