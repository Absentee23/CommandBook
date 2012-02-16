// $Id$
/*
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
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

package com.sk89q.commandbook.session;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.spout.api.command.CommandSource;
import org.spout.api.entity.Position;
import org.spout.api.exception.CommandException;
import org.spout.api.geo.discrete.atomic.Transform;
import org.spout.api.player.Player;

public class UserSession implements PersistentSession {

    public static final String CONSOLE_NAME = "#console";
    private static final int MAX_AGE = 600000;
    private static final int RECONNECT_GRACE = 60000;
    private static final int BRINGABLE_TIME = 300000;
    private static final int TP_REQUEST_WAIT_TIME = 30000;
    private static final int LOCATION_HISTORY_SIZE = 10;

    private long lastUpdate = 0;
    private String lastRecipient = null;
    private long lastRecipientTime = 0;
    private boolean hasThor = false;
    private String idleStatus = null;
    private String commandToConfirm;
    private Map<String, Long> bringable = new HashMap<String, Long>();
    private final Map<String, Long> teleportRequests = new HashMap<String, Long>();
    private final LinkedList<Position> locationHistory = new LinkedList<Position>();
    private Position ignoreTeleportLocation;
    
    public boolean isRecent() {
        return (System.currentTimeMillis() - lastUpdate) < MAX_AGE;
    }
    
    public void handleReconnect() {
        if ((System.currentTimeMillis() - lastUpdate) >= RECONNECT_GRACE) {
            lastRecipient = null;
            bringable = new HashMap<String, Long>();
        }
    }
    
    public void handleDisconnect() {
        lastUpdate = System.currentTimeMillis();
        
        hasThor = false;
    }
    
    public String getLastRecipient() {
        return lastRecipient;
    }

    public void setLastRecipient(CommandSource target) {
        if (target instanceof Player) {
            lastRecipient = target.getName();
        } else {
            lastRecipient = CONSOLE_NAME;
        }
    }
    
    public void setNewLastRecipient(CommandSource target) {
        long now = System.currentTimeMillis();
        
        if (lastRecipient == null || (now - lastRecipientTime) > 1000) {
            setLastRecipient(target);
            lastRecipientTime = now;
        }
    }
    
    public boolean hasThor() {
        return hasThor;
    }
    
    public void setHasThor(boolean hasThor) {
        this.hasThor = hasThor;
    }
    
    public void addBringable(Player player) {
        bringable.put(player.getName(), System.currentTimeMillis());
    }
    
    public void removeBringable(Player player) {
        bringable.put(player.getName(), System.currentTimeMillis());
    }

    public boolean isBringable(Player player) {
        long now = System.currentTimeMillis();
        Long time = bringable.remove(player.getName());
        return (time != null && (now - time) < BRINGABLE_TIME);
    }

    public void checkLastTeleportRequest(Player target) throws CommandException {
        long now = System.currentTimeMillis();
        Long time = teleportRequests.remove(target.getName());
        if (time != null && (now - time) < TP_REQUEST_WAIT_TIME) {
            throw new CommandException("Wait a bit before asking again.");
        }
        teleportRequests.put(target.getName(), now);
    }
    
    public void rememberLocation(Position location) {
        if (locationHistory.size() > 0 && locationHistory.peek().equals(location)) {
            return;
        }
        
        locationHistory.add(0, location);
        while (locationHistory.size() > LOCATION_HISTORY_SIZE) {
            locationHistory.poll();
        }
    }
    
    public void rememberLocation(Player player) {
        rememberLocation(player.getEntity().getPosition());
    }
    
    public Position popLastLocation() {
        return locationHistory.poll();
    }
    
    public String getIdleStatus() {
        return this.idleStatus;
    }

    public void setIdleStatus(String status) {
        this.idleStatus = status;
    }

    public void setIgnoreLocation(Position loc) {
        this.ignoreTeleportLocation = loc;
    }

    public Position getIgnoreLocation() {
        return ignoreTeleportLocation;
    }

    public boolean checkOrQueueConfirmed(String command) {
        if (commandToConfirm != null) {
            return true;
        } else {
            commandToConfirm = command;
            return false;
        }
    }

    public String getCommandToConfirm(boolean clear) {
        if (clear) {
            final String ret = commandToConfirm;
            commandToConfirm = null;
            return ret;
        }
        return commandToConfirm;
    }
}