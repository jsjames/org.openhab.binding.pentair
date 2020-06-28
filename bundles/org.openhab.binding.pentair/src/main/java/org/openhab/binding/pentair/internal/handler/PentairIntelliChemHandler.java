/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.pentair.internal.handler;

import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.pentair.internal.PentairIntelliChem;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairIntelliChemHandler} is responsible for implementation of the IntelliChemp. This will
 * parse of status packets to set the stat for various channels.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairIntelliChemHandler extends PentairBaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChemHandler.class);

    private boolean waitStatusForOnline = false;
    protected PentairIntelliChem pic = new PentairIntelliChem();

    public PentairIntelliChemHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Intelliflo - Thing ID: {}.", this.getThing().getUID());

        Configuration config = getConfig();

        id = ((BigDecimal) config.get("id")).intValue();

        @Nullable
        PentairBaseBridgeHandler bh = getBridgeHandler();

        if (bh == null) {
            logger.debug("Bridge does not exist and intellichem cannot be intiailized");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Intellichem cannot be created without a bridge.");
            return;
        }

        if (bh.equipment.get(id) != null) {
            logger.debug("Another intellichem with the same ID ({}) has already been initialized.", id);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another intellichem with identical ID has been initialized.");
            return;
        }

        bh.equipment.put(id, this);

        goOnline();
    }

    @Override
    public void dispose() {
        logger.debug("Thing {} disposed.", getThing().getUID());

        PentairBaseBridgeHandler bh = getBridgeHandler();
        if (bh != null) {
            bh.equipment.remove(id);
        }

        goOffline(ThingStatusDetail.NONE);
    }

    public void goOnline() {
        logger.debug("Thing {} goOnline.", getThing().getUID());

        this.waitStatusForOnline = false;

        /*
         * if (bth != this) {
         * logger.debug("Another IntelliChem has already been configured");
         * updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
         * "Another IntelliChem is already configured.");
         * }
         */

        PentairBaseBridgeHandler bh = getBridgeHandler();
        if (bh == null) {
            logger.error("Bridge does not exist");
            return;
        }

        ThingStatus ts = bh.getThing().getStatus();
        if (!ts.equals(ThingStatus.ONLINE)) {
            logger.debug("Bridge is not online");
            return;
        }

        waitStatusForOnline = true;
    }

    public void finishOnline() {
        updateStatus(ThingStatus.ONLINE);
    }

    public void goOffline(ThingStatusDetail detail) {
        logger.debug("Thing {} goOffline.", getThing().getUID());

        updateStatus(ThingStatus.OFFLINE, detail);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.trace("PentairIntelliFloHandler: bridgeStatusChanged");

        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand, {}, {}", channelUID, command);

        // all fields are read only
        if (!(command instanceof RefreshType)) {
            return;
        }

        switch (channelUID.getId()) {
            case INTELLICHEM_PHREADING:
                updateChannel(INTELLICHEM_PHREADING, pic.phreading);
                break;
            case INTELLICHEM_ORPREADING:
                updateChannel(INTELLICHEM_ORPREADING, pic.orpreading);
                break;
            case INTELLICHEM_PHSETPOINT:
                updateChannel(INTELLICHEM_PHSETPOINT, pic.phsetpoint);
                break;
            case INTELLICHEM_ORPSETPOINT:
                updateChannel(INTELLICHEM_ORPSETPOINT, pic.orpsetpoint);
                break;
            case INTELLICHEM_TANK1:
                updateChannel(INTELLICHEM_TANK1, pic.tank1);
                break;
            case INTELLICHEM_TANK2:
                updateChannel(INTELLICHEM_TANK2, pic.tank2);
                break;
            case INTELLICHEM_CALCIUMHARDNESS:
                updateChannel(INTELLICHEM_CALCIUMHARDNESS, pic.calciumhardness);
                break;
            case INTELLICHEM_CYAREADING:
                updateChannel(INTELLICHEM_CYAREADING, pic.cyareading);
                break;
            case INTELLICHEM_TOTALALKALINITY:
                updateChannel(INTELLICHEM_TOTALALKALINITY, pic.totalalkalinity);
                break;
            case INTELLICHEM_WATERFLOWALARM:
                updateChannel(INTELLICHEM_WATERFLOWALARM, pic.waterflowalarm);
                break;
            case INTELLICHEM_MODE1:
                updateChannel(INTELLICHEM_MODE1, pic.mode1);
                break;
            case INTELLICHEM_MODE2:
                updateChannel(INTELLICHEM_MODE2, pic.mode2);
                break;
            case INTELLICHEM_SATURATIONINDEX:
                updateChannel(INTELLICHEM_SATURATIONINDEX, pic.saturationindex);
                break;
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {
        if (waitStatusForOnline) {
            finishOnline();
            waitStatusForOnline = false;
        }

        switch (p.getAction()) {
            case 0x12: // A5 10 09 10 E3 02 AF 02 EE 02 BC 00 00 00 02 00 00 00 2A 00 04 00 5C 06 05 18 01 90 00 00 00
                       // 96
                       // 14 00 51 00 00 65 20 3C 01 00 00 00

                pic.parsePacket(p);
                logger.debug("Intellichem status: {}: ", pic.toString());

                updateChannel(INTELLICHEM_PHREADING, pic.phreading);
                updateChannel(INTELLICHEM_ORPREADING, pic.orpreading);
                updateChannel(INTELLICHEM_PHSETPOINT, pic.phsetpoint);
                updateChannel(INTELLICHEM_ORPSETPOINT, pic.orpsetpoint);
                updateChannel(INTELLICHEM_TANK1, pic.tank1);
                updateChannel(INTELLICHEM_TANK2, pic.tank2);
                updateChannel(INTELLICHEM_CALCIUMHARDNESS, pic.calciumhardness);
                updateChannel(INTELLICHEM_CYAREADING, pic.cyareading);
                updateChannel(INTELLICHEM_TOTALALKALINITY, pic.totalalkalinity);
                updateChannel(INTELLICHEM_WATERFLOWALARM, pic.waterflowalarm);
                updateChannel(INTELLICHEM_MODE1, pic.mode1);
                updateChannel(INTELLICHEM_MODE2, pic.mode2);
                updateChannel(INTELLICHEM_SATURATIONINDEX, pic.saturationindex);

                break;

            default:
                logger.debug("Unhandled Intellichem packet: {}", p.toString());
                break;
        }
    }
}