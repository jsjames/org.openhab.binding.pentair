/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pentair.internal.handler;

import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.pentair.internal.PentairControllerConstants;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.PentairPacketControllerSchedule;
import org.openhab.binding.pentair.internal.PentairPacketHeatSetPoint;
import org.openhab.binding.pentair.internal.PentairPacketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairControllerHandler} is responsible for implementation of the EasyTouch Controller. It will handle
 * commands sent to a thing and implements the different channels. It also parses/disposes of the packets seen on the
 * bus from the controller.
 *
 * @author Jeff James - Initial contribution
 */
public class PentairControllerHandler extends PentairBaseThingHandler {

    protected static final int NUMCIRCUITS = 8;
    protected static final int NUMSCHEDULES = 9;

    // only one controller can be online at a time, used to validate only one is online & to access status
    public static PentairControllerHandler onlineController;
    public boolean servicemode = false;

    private final Logger logger = LoggerFactory.getLogger(PentairControllerHandler.class);
    protected ScheduledFuture<?> syncTimeJob;
    private int preambleByte = -1; // Byte to use after 0xA5 in communicating to controller. Not sure why this changes,
                                   // but it requires to be in sync and up-to-date
    private boolean waitStatusForOnline = false; // To manage online status, only go online when we received first
                                                 // status command

    /**
     * current/last status packet recieved, used to compare new packet values to determine if status needs to be updated
     */
    protected PentairPacketStatus p29cur = new PentairPacketStatus();
    protected PentairPacketStatus p29old;
    /** current/last heat set point packet, used to determine if status in framework should be updated */
    protected PentairPacketHeatSetPoint phspcur = new PentairPacketHeatSetPoint();

    protected PentairPacketControllerSchedule[] schedules = new PentairPacketControllerSchedule[NUMSCHEDULES];

    public PentairControllerHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Controller - Thing ID: {}.", this.getThing().getUID());

        goOnline();
    }

    @Override
    public void dispose() {
        logger.debug("Thing {} disposed.", getThing().getUID());

        goOffline(ThingStatusDetail.NONE);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged: {}", bridgeStatusInfo);

        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        }
    }

    public void goOnline() {
        logger.debug("Thing {} goOnline.", getThing().getUID());

        if (onlineController != null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another Controller controller is already configured.");
        }

        id = ((BigDecimal) getConfig().get("id")).intValue();

        // make sure bridge exists and is online
        Bridge bridge = this.getBridge();
        if (bridge == null) {
            return;
        }
        PentairBaseBridgeHandler bh = (PentairBaseBridgeHandler) bridge.getHandler();
        if (bh == null) {
            logger.debug("Bridge does not exist");
            return;
        }

        ThingStatus ts = bh.getThing().getStatus();
        if (!ts.equals(ThingStatus.ONLINE)) {
            logger.debug("Bridge is not online");
            return;
        }

        waitStatusForOnline = true; // Wait for first status response to go online
    }

    public void finishOnline() {
        // update status to ONLINE even though we haven't queried all info
        onlineController = this;
        updateStatus(ThingStatus.ONLINE);

        // setup timer to sync time
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                boolean synctime = ((boolean) getConfig().get("synctime"));
                if (synctime) {
                    logger.info("Synchronizing System Time");
                    Calendar now = Calendar.getInstance();
                    setClockSettings(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                            now.get(Calendar.DAY_OF_WEEK), now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1,
                            now.get(Calendar.YEAR) - 2000);
                }
            }
        };

        // setup syncTimeJob to run once a day, initial time to sync is 3 minutes after controller goes online. This is
        // to prevent collision with main thread queries on initial startup
        syncTimeJob = scheduler.scheduleAtFixedRate(runnable, 3, 24 * 60 * 60, TimeUnit.MINUTES);

        Runnable queryinfo = new Runnable() {
            @Override
            public void run() {
                int i;

                getSWVersion();
                delay300();

                getHeat();
                delay300();

                for (i = 1; i <= NUMCIRCUITS; i++) {
                    getCircuitNameFunction(i);
                    delay300();
                }

                for (i = 1; i <= NUMSCHEDULES; i++) {
                    getSchedule(i);
                    delay300();
                }
            }
        };
        Thread thread = new Thread(queryinfo);
        thread.start();
    }

    public void goOffline(ThingStatusDetail detail) {
        logger.debug("Thing {} goOffline.", getThing().getUID());

        if (syncTimeJob != null) {
            syncTimeJob.cancel(true);
        }

        onlineController = null;
        updateStatus(ThingStatus.OFFLINE, detail);
    }

    public int getCircuitNumber(String name) {
        switch (name) {
            case CONTROLLER_POOLCIRCUIT:
                return 6;
            case CONTROLLER_SPACIRCUIT:
                return 1;
            case CONTROLLER_AUX1CIRCUIT:
                return 2;
            case CONTROLLER_AUX2CIRCUIT:
                return 3;
            case CONTROLLER_AUX3CIRCUIT:
                return 4;
            case CONTROLLER_AUX4CIRCUIT:
                return 5;
            case CONTROLLER_AUX5CIRCUIT:
                return 7;
            case CONTROLLER_AUX6CIRCUIT:
                return 8;
            case CONTROLLER_AUX7CIRCUIT:
                return 9;
        }

        return 0;
    }

    public int getScheduleNumber(String name) {
        int i;

        for (i = 1; i <= NUMSCHEDULES; i++) {
            String str = String.format(CONTROLLER_SCHEDULE, i);
            if (str.equals(name)) {
                return i;
            }
        }

        return 0;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.trace("Controller received refresh command");

            // updateChannel(channelUID.getId(), null);

            return;
        }

        switch (channelUID.getIdWithoutGroup()) {
            case CONTROLLER_CIRCUITSWITCH: {
                int circuit = getCircuitNumber(channelUID.getGroupId());

                boolean state = ((OnOffType) command) == OnOffType.ON;
                circuitSwitch(circuit, state);

                break;
            }
            case CONTROLLER_LIGHTMODE: {
                String str = ((StringType) command).toString();

                int mode = PentairControllerConstants.LIGHTMODES_INV.get(str);
                setLightMode(mode);

                break;
            }
            case CONTROLLER_SCHEDULESTRING: {
                String str = ((StringType) command).toString();

                int schedule = getScheduleNumber(channelUID.getGroupId());
                if (schedule == 0) {
                    break;
                }

                PentairPacketControllerSchedule ppcs = schedules[schedule - 1];

                ppcs.ParseString(str);

                break;
            }
            case CONTROLLER_SETPOINT: {
                if (!(command instanceof DecimalType)) {
                    break;
                }

                int sp = ((DecimalType) command).intValue();

                if (sp == 0) {
                    return;
                }

                String groupId = channelUID.getGroupId();
                if (groupId == null) {
                    return;
                }
                switch (groupId) {
                    case CONTROLLER_SPAHEAT:
                        setPoint(false, sp);
                        break;

                    case CONTROLLER_POOLHEAT:
                        setPoint(true, sp);
                        break;
                }

                break;
            }
        }
    }

    /* Commands to send to Controller */

    /**
     * Method to turn on/off a circuit in response to a command from the framework
     *
     * @param circuit circuit number
     * @param state
     */
    public void circuitSwitch(int circuit, boolean state) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x86,
                (byte) 0x02, (byte) circuit, (byte) ((state) ? 1 : 0) };

        logger.info("circuit Switch: {}, {}", circuit, state);
        writePacket(packet);
    }

    /**
     * Method to request clock
     */
    public void getClockSettings() { // A5 01 10 20 C5 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xC5,
                (byte) 0x01, (byte) 0x00 };

        logger.info("Request clock settings");
        writePacket(packet);
    }

    public void getControllerStatus() { // A5 01 10 20 02 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x02,
                (byte) 0x01, (byte) 0x00 };

        logger.info("Request controller status");
        writePacket(packet);
    }

    public void getLightGroups() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xE7,
                (byte) 0x01, (byte) 0x00 };

        logger.info("Get Light Groups");

        writePacket(packet);
    }

    public void setLightMode(int mode) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x60,
                (byte) 0x02, (byte) mode, (byte) 0x00 };

        logger.info("setLightMode: {}", mode);

        writePacket(packet);
    }

    public void getCircuitNameFunction(int circuit) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xCB,
                (byte) 0x01, (byte) circuit };

        logger.info("getCircuitNameFunction: {}", circuit);

        writePacket(packet);
    }

    public void getSchedule(int num) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xD1,
                (byte) 0x01, (byte) num };

        logger.info("getSchedule: {}", num);

        writePacket(packet);
    }

    public void getSWVersion() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xD9,
                (byte) 0x01, (byte) 0x00 };

        logger.info("getSWVersion");

        writePacket(packet);
    }

    /**
     * Method to set clock
     *
     */
    public void setClockSettings(int hour, int min, int dow, int day, int month, int year) { // A5 01 10 20 85 08 0D 2A
                                                                                             // 02 1D 04 11 00 00

        logger.info("Set Clock Settings {}:{} {} {}/{}/{}", hour, min, dow, day, month, year);

        if (hour > 23) {
            throw new IllegalArgumentException("hour not in range [0..23]: " + hour);
        }
        if (min > 59) {
            throw new IllegalArgumentException("hour not in range [0..59]: " + min);
        }
        if (dow > 7 || dow < 1) {
            throw new IllegalArgumentException("hour not in range [1..7]: " + dow);
        }
        if (day > 31 || day < 1) {
            throw new IllegalArgumentException("hour not in range [1..31]: " + day);
        }
        if (month > 12 || month < 1) {
            throw new IllegalArgumentException("hour not in range [1..12]: " + month);
        }
        if (year > 99) {
            throw new IllegalArgumentException("hour not in range [0..99]: " + year);
        }

        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x85,
                (byte) 0x08, (byte) hour, (byte) min, (byte) dow, (byte) day, (byte) month, (byte) year, (byte) 0x00,
                (byte) 0x00 };

        writePacket(packet);
    }

    public void getHeat() { // A5 01 10 20 C8 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xC8,
                (byte) 0x01, (byte) 0 };

        logger.info("Get heat settings");

        writePacket(packet);
    }

    /**
     * Method to set heat point for pool (true) of spa (false)
     *
     * @param Pool pool=true, spa=false
     * @param temp
     */
    public void setPoint(boolean pool, int temp) {
        // [16,34,136,4,POOL HEAT Temp,SPA HEAT Temp,Heat Mode,0,2,56]
        // [165, preambleByte, 16, 34, 136, 4, currentHeat.poolSetPoint, parseInt(req.params.temp), updateHeatMode, 0]
        int spaset = (!pool) ? temp : phspcur.spasetpoint;
        int poolset = (pool) ? temp : phspcur.poolsetpoint;
        int heatmode = (phspcur.spaheatmode << 2) | phspcur.poolheatmode;

        if (temp < 50 || temp > 105) {
            return;
        }

        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x88,
                (byte) 0x04, (byte) poolset, (byte) spaset, (byte) heatmode, (byte) 0 };

        logger.info("Set {} temperature: {}", (pool) ? "Pool" : "Spa", temp);

        writePacket(packet);
    }

    @Override
    public void processPacketFrom(PentairPacket p) {

        switch (p.getAction()) {
            case 1: // Ack
                logger.debug("Ack command from device: {} - {}", p.getByte(PentairPacket.STARTOFDATA), p);
                break;
            case 2: // Controller Status
                if (p.getLength() != 29) {
                    logger.debug("Expected length of 29: {}", p);
                    return;
                }

                logger.trace("Controller Status: {}", p);

                preambleByte = p.getByte(1); // Adjust what byte is used for preamble
                if (waitStatusForOnline) {
                    waitStatusForOnline = false;
                    finishOnline();
                }

                p29cur = new PentairPacketStatus(p);

                // only update packet of value has changed
                if (p29cur.equals(p29old)) {
                    return;
                }
                p29old = p29cur;

                updateChannel(CONTROLLER_POOLCIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.pool);
                updateChannel(CONTROLLER_SPACIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.spa);
                updateChannel(CONTROLLER_AUX1CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux1);
                updateChannel(CONTROLLER_AUX2CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux2);
                updateChannel(CONTROLLER_AUX3CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux3);
                updateChannel(CONTROLLER_AUX4CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux4);
                updateChannel(CONTROLLER_AUX5CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux5);
                updateChannel(CONTROLLER_AUX6CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux6);
                updateChannel(CONTROLLER_AUX7CIRCUIT, CONTROLLER_CIRCUITSWITCH, p29cur.aux7);

                updateState(CONTROLLER_POOLHEAT + "#" + CONTROLLER_TEMPERATURE,
                        (p29cur.pool) ? new DecimalType(p29cur.pooltemp) : UnDefType.UNDEF);
                updateState(CONTROLLER_SPAHEAT + "#" + CONTROLLER_TEMPERATURE,
                        (p29cur.spa) ? new DecimalType(p29cur.spatemp) : UnDefType.UNDEF);

                updateChannel(CONTROLLER_STATUS, CONTROLLER_AIRTEMPERATURE, p29cur.airtemp);
                updateChannel(CONTROLLER_STATUS, CONTROLLER_SOLARTEMPERATURE, p29cur.solartemp);
                updateChannel(CONTROLLER_STATUS, CONTROLLER_UOM, (p29cur.uom) ? "CELCIUS" : "FARENHEIT");
                updateChannel(CONTROLLER_STATUS, CONTROLLER_SERVICEMODE, p29cur.servicemode);
                servicemode = p29cur.servicemode;

                updateChannel(CONTROLLER_STATUS, CONTROLLER_SOLARON, p29cur.solaron);
                updateChannel(CONTROLLER_STATUS, CONTROLLER_HEATERON, p29cur.heateron);

                break;
            case 4: // Pump control panel on/off
                // Controller sends packet often to keep control of the motor
                logger.debug("Pump control panel on/of {}: {}", p.getDest(), p.getByte(PentairPacket.STARTOFDATA));

                break;
            case 5: // Current Clock - A5 01 0F 10 05 08 0E 09 02 1D 04 11 00 00 - H M DOW D M YY YY ??
                int hour = p.getByte(PentairPacket.STARTOFDATA + 0);
                int minute = p.getByte(PentairPacket.STARTOFDATA + 1);
                int dow = p.getByte(PentairPacket.STARTOFDATA + 2);
                int day = p.getByte(PentairPacket.STARTOFDATA + 3);
                int month = p.getByte(PentairPacket.STARTOFDATA + 4);
                int year = p.getByte(PentairPacket.STARTOFDATA + 5);

                logger.debug("System Clock: {}:{} {} {}/{}/{}", hour, minute, dow, day, month, year);

                break;
            case 6: // Set run mode
                // No action - have not verified these commands, here for documentation purposes and future enhancement
                logger.debug("Set run mode {}: {}", p.getDest(), p.getByte(PentairPacket.STARTOFDATA));

                break;
            case 7: // Pump Status
                // No action - have not verified these commands, here for documentation purposes and future enhancement
                logger.debug("Pump request status (unseen): {}", p);
                break;
            case 8: // Heat Status - A5 01 0F 10 08 0D 4B 4B 4D 55 5E 07 00 00 58 00 00 00 
                if (p.getLength() != 0x0D) {
                    logger.debug("Expected length of 13: {}", p);
                    return;
                }

                phspcur = new PentairPacketHeatSetPoint(p);

                updateChannel(CONTROLLER_POOLHEAT, CONTROLLER_SETPOINT, phspcur.poolsetpoint);
                updateChannel(CONTROLLER_SPAHEAT, CONTROLLER_SETPOINT, phspcur.spasetpoint);

                updateChannel(CONTROLLER_POOLHEAT, CONTROLLER_HEATMODE,
                        PentairControllerConstants.HEATMODE.get(phspcur.poolheatmode));
                updateChannel(CONTROLLER_SPAHEAT, CONTROLLER_HEATMODE,
                        PentairControllerConstants.HEATMODE.get(phspcur.spaheatmode));

                logger.debug("Heat set point: {}, {}, {}", p, phspcur.poolsetpoint, phspcur.spasetpoint);
                break;
            case 10: // Custom Names
                logger.debug("Get Custom Names (unseen): {}", p);
                break;
            case 11: // Circuit Names
                int index;
                String name;
                String function;

                index = p.getByte(PentairPacket.STARTOFDATA + 2);
                name = PentairControllerConstants.CIRCUITNAME.get(index);

                index = p.getByte(PentairPacket.STARTOFDATA + 1);
                function = PentairControllerConstants.CIRCUITFUNCTION.get(index);

                logger.debug("Circuit Names - Circuit: {}, Function: {}, Name: {}",
                        p.getByte(PentairPacket.STARTOFDATA), function, name);
                break;
            case 17: // A5 1E 0F 10 11 07 01 06 0B 00 0F 00 7F
                PentairPacketControllerSchedule ppcs;
                String schedulestr;

                ppcs = new PentairPacketControllerSchedule(p);
                if (ppcs.id > NUMSCHEDULES) {
                    return;
                }

                schedules[ppcs.id - 1] = ppcs;

                schedulestr = ppcs.toString();

                String group = String.format(CONTROLLER_SCHEDULE, ppcs.id);

                updateChannel(group, CONTROLLER_SCHEDULESTRING, schedulestr);

                updateChannel(group, CONTROLLER_SCHEDULETYPE, ppcs.GetTypeString());
                updateChannel(group, CONTROLLER_SCHEDULECIRCUIT, ppcs.circuit);
                updateChannel(group, CONTROLLER_SCHEDULEDAYS, ppcs.days);
                updateChannel(group, CONTROLLER_SCHEDULESTARTHOUR, ppcs.starth);
                updateChannel(group, CONTROLLER_SCHEDULESTARTMIN, ppcs.startm);
                updateChannel(group, CONTROLLER_SCHEDULEENDHOUR, ppcs.endh);
                updateChannel(group, CONTROLLER_SCHEDULEENDMIN, ppcs.endm);
                updateChannel(group, CONTROLLER_SCHEDULEDAYS, ppcs.days);

                logger.debug(
                        "Controller Schedule - ID: {}, Type: {}, Circuit: {}, Start Time: {}:{}, End Time: {}:{}, Days: {}",
                        ppcs.id, ppcs.type, ppcs.circuit, ppcs.starth, ppcs.startm, ppcs.endh, ppcs.endm, ppcs.days);

                logger.info(schedulestr);
                break;
            case 25: // Intellichlor status
                logger.debug("Intellichlor status: {}", p);

            case 39: // Light Groups/Positions
                logger.debug("Light Groups/Positions (unseen); {}", p);
                break;
            case 40: // Settings?
                logger.debug("Settings?: {}", p);
                break;
            case 134:
                logger.debug("Set Circuit Function On/Off (unseen): {}", p);
                break;
            case 252:
                logger.debug("SW Version - {}", p);
                break;
            default:
                logger.debug("Not Implemented {}: {}", p.getAction(), p);
                break;
        }
    }

    /**
     * Helper function to update channel.
     */
    public void updateChannel(String group, String channel, boolean value) {
        updateState(group + "#" + channel, (value) ? OnOffType.ON : OnOffType.OFF);
    }

    public void updateChannel(String group, String channel, int value) {
        updateState(group + "#" + channel, new DecimalType(value));
    }

    public void updateChannel(String group, String channel, String value) {
        updateState(group + "#" + channel, new StringType(value));
    }
}
