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

package com.sk89q.commandbook.locations;

import com.sk89q.commandbook.CommandBook;
import com.sk89q.commandbook.commands.PaginatedResult;
import com.sk89q.minecraft.util.commands.NestedCommand;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.InjectComponent;
import com.sk89q.commandbook.session.SessionComponent;
import com.sk89q.commandbook.util.LocationUtil;
import com.sk89q.commandbook.util.PlayerUtil;
import com.sk89q.commandbook.util.TeleportPlayerIterator;
import org.spout.api.ChatColor;
import org.spout.api.command.CommandContext;
import org.spout.api.command.CommandSource;
import org.spout.api.command.annotated.Command;
import org.spout.api.command.annotated.CommandPermissions;
import org.spout.api.exception.CommandException;
import org.spout.api.geo.World;
import org.spout.api.geo.discrete.atomic.Transform;
import org.spout.api.player.Player;

@ComponentInformation(friendlyName = "Warps", desc = "Provides warps functionality")
public class WarpsComponent extends LocationsComponent {
    
    @InjectComponent private SessionComponent sessions;
    public WarpsComponent() {
        super("Warp");
    }

    public void enable() {
        super.enable();
        registerCommands(Commands.class);
    }

    public class Commands {
        @Command(aliases = {"warp"},
                usage = "[world] [target] <warp>", desc = "Teleport to a warp",
                flags = "s", min = 1, max = 3)
        @CommandPermissions({"commandbook.warp.teleport"})
        public void warp(CommandContext args, CommandSource sender) throws CommandException {
            Iterable<Player> targets = null;
            NamedLocation warp = null;
            Transform loc;

            // Detect arguments based on the number of arguments provided
            if (args.length() == 1) {
                Player player = PlayerUtil.checkPlayer(sender);
                targets = PlayerUtil.matchPlayers(player);
                warp = getManager().get(player.getEntity().getWorld(), args.getString(0));
            } else if (args.length() == 2) {
                targets = PlayerUtil.matchPlayers(sender, args.getString(0));
                if (getManager().isPerWorld()) {
                    Player player = PlayerUtil.checkPlayer(sender);
                    warp = getManager().get(player.getEntity().getWorld(), args.getString(1));
                } else {
                    warp = getManager().get(null, args.getString(1));
                }
            } else if (args.length() == 3) {
                targets = PlayerUtil.matchPlayers(sender, args.getString(1));
                warp = getManager().get(
                        LocationUtil.matchWorld(sender, args.getString(0)), args.getString(2));
            }

            // Check permissions!
            for (Player target : targets) {
                if (target != sender) {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.teleport.other");
                    break;
                }
            }

            if (warp != null) {
                loc = warp.getLocation();
            } else {
                throw new CommandException("A warp by the given name does not exist.");
            }

            (new TeleportPlayerIterator(sender, loc, args.hasFlag('s'))).iterate(targets);
        }

        @Command(aliases = {"setwarp"}, usage = "<warp> [location]", desc = "Set a warp", min = 1, max = 2)
        @CommandPermissions({"commandbook.warp.set"})
        public void setWarp(CommandContext args, CommandSource sender) throws CommandException {
            String warpName = args.getString(0);
            Transform loc;
            Player player = null;

            // Detect arguments based on the number of arguments provided
            if (args.length() == 1) {
                player = PlayerUtil.checkPlayer(sender);
                loc = player.getEntity().getTransform();
            } else {
                loc = LocationUtil.matchLocation(sender, args.getString(1));
            }
            NamedLocation existing = getManager().get(loc.getPosition().getWorld(), warpName);
            if (existing != null) {
                if (!existing.getCreatorName().equals(sender.getName())) {
                    CommandBook.inst().checkPermission(sender, "commandbook.warp.set.override");
                }
                if (!sessions.getSession(sender).checkOrQueueConfirmed(args.getCommand() + " " + args.getJoinedString(0))) {
                    throw new CommandException("Warp already exists! Type /confirm to confirm overwriting");
                }
            }

            getManager().create(warpName, loc, player);

            sender.sendMessage(ChatColor.YELLOW + "Warp '" + warpName + "' created.");
        }

        @Command(aliases = {"warps"}, desc = "Warp management")
        @NestedCommand({ManagementCommands.class})
        public void warps(CommandContext args, CommandSource sender) throws CommandException {
        }
    }
    
    public class ManagementCommands {
        @Command(aliases = {"del", "delete", "remove", "rem"}, usage = "<warpname> [world]",
                desc = "Remove a warp", min = 1, max = 2 )
        @CommandPermissions({"commandbook.remove"})
        public void removeCmd(CommandContext args, CommandSource sender) throws CommandException {
            World world;
            String warpName = args.getString(0);
            if (args.length() == 2) {
                world = LocationUtil.matchWorld(sender, args.getString(1));
            } else {
                world = PlayerUtil.checkPlayer(sender).getEntity().getWorld();
            }
            remove(warpName, world, sender);
        }


        @Command(aliases = {"list", "show"}, usage = "[ -p owner] [-w world] [page]",
                desc = "List warps", flags = "p:w:", min = 0, max = 1 )
        @CommandPermissions({"commandbook.warp.list"})
        public void listCmd(CommandContext args, CommandSource sender) throws CommandException {
            list(args, sender);
        }
    }

    @Override
    public PaginatedResult<NamedLocation> getListResult() {
        final String defaultWorld = CommandBook.game().getWorlds().iterator().next().getName();
        return new PaginatedResult<NamedLocation>("Name - Owner - World  - Location") {
            @Override
            public String format(NamedLocation entry) {
                return entry.getName()
                        + " - " + entry.getCreatorName()
                        + " - " + (entry.getWorldName() == null ? defaultWorld : entry.getWorldName())
                        + " - " + entry.getLocation().getPosition().getX() + "," + entry.getLocation().getPosition().getY()
                        + "," + entry.getLocation().getPosition().getZ();
            }
        };
    }
}
