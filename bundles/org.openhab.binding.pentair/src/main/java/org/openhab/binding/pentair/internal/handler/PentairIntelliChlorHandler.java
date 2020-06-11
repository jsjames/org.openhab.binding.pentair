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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.pentair.internal.PentairIntelliChlor;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairIntelliChlorHandler} is responsible for implementation of the Intellichlor Salt generator. It will
 * process
 * Intellichlor commands and set the appropriate channel states. There are currently no commands implemented for this
 * Thing to receive from the framework.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairIntelliChlorHandler extends PentairBaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChlorHandler.class);
    private boolean waitStatusForOnline = false;

    @Nullable
    public static PentairIntelliChlorHandler onlineChlorinator;

    public PentairIntelliChlor pic = new PentairIntelliChlor();

    public PentairIntelliChlorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing IntelliChlor - Thing ID: {}.", this.getThing().getUID());

        id = 0; // Intellichlor doesn't have ID

        @Nullable
        PentairBaseBridgeHandler bh = getBridgeHandler();

        if (bh == null) {
            logger.debug("Bridge does not exist and IntelliChlor cannot be intiailized");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "IntelliChlor cannot be created without a bridge.");
            return;
        }

        if (bh.equipment.get(id) != null) {
            logger.debug("Another IntelliChlor has already been initialized {}.", id);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another IntelliChlor has already been initialized.");
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

        if (onlineChlorinator != null) {
            logger.debug("Another IntelliChlor has already been configured");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another IntelliChlor is already configured.");
            return;
        }

        waitStatusForOnline = true;
    }

    public void goOffline(ThingStatusDetail detail) {
        logger.debug("Thing {} goOffline.", getThing().getUID());

        onlineChlorinator = null;

        updateStatus(ThingStatus.OFFLINE, detail);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.trace("PentairIntelliChlorHandler: bridgeStatusChanged");

        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.trace("IntelliChlor received refresh command");

            switch (channelUID.getId()) {
                case INTELLICHLOR_SALTOUTPUT:
                    updateChannel(INTELLICHLOR_SALTOUTPUT, pic.saltoutput);
                    break;
                case INTELLICHLOR_SALINITY:
                    updateChannel(INTELLICHLOR_SALINITY, pic.salinity);
                    break;
                case INTELLICHLOR_OK:
                    updateChannel(INTELLICHLOR_OK, pic.ok);
                    break;
                case INTELLICHLOR_LOWFLOW:
                    updateChannel(INTELLICHLOR_LOWFLOW, pic.lowflow);
                    break;
                case INTELLICHLOR_LOWSALT:
                    updateChannel(INTELLICHLOR_LOWSALT, pic.lowsalt);
                    break;
                case INTELLICHLOR_VERYLOWSALT:
                    updateChannel(INTELLICHLOR_VERYLOWSALT, pic.verylowsalt);
                    break;
                case INTELLICHLOR_HIGHCURRENT:
                    updateChannel(INTELLICHLOR_HIGHCURRENT, pic.highcurrent);
                    break;
                case INTELLICHLOR_CLEANCELL:
                    updateChannel(INTELLICHLOR_CLEANCELL, pic.cleancell);
                    break;
                case INTELLICHLOR_LOWVOLTAGE:
                    updateChannel(INTELLICHLOR_LOWVOLTAGE, pic.lowvoltage);
                    break;
                case INTELLICHLOR_LOWWATERTEMP:
                    updateChannel(INTELLICHLOR_LOWWATERTEMP, pic.lowwatertemp);
                    break;
                case INTELLICHLOR_COMMERROR:
                    updateChannel(INTELLICHLOR_COMMERROR, pic.commerror);
                    break;
            }
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {

        pic.parsePacket(p);
        switch (p.buf[PentairIntelliChlor.ACTION]) {
            case 0x11: // set salt output % command
                pic.parsePacket(p);
                updateChannel(INTELLICHLOR_SALTOUTPUT, pic.saltoutput);
                logger.debug("Intellichlor set output % {}", pic.saltoutput);
                break;
            case 0x12: // response to set salt output
                if (waitStatusForOnline) { // Only go online after first response from the Intellichlor
                    updateStatus(ThingStatus.ONLINE);
                    onlineChlorinator = this;
                    waitStatusForOnline = false;
                }

                pic.parsePacket(p);

                updateChannel(INTELLICHLOR_SALINITY, pic.salinity);
                updateChannel(INTELLICHLOR_OK, pic.ok);
                updateChannel(INTELLICHLOR_LOWFLOW, pic.lowflow);
                updateChannel(INTELLICHLOR_LOWSALT, pic.lowsalt);
                updateChannel(INTELLICHLOR_VERYLOWSALT, pic.verylowsalt);
                updateChannel(INTELLICHLOR_HIGHCURRENT, pic.highcurrent);
                updateChannel(INTELLICHLOR_CLEANCELL, pic.cleancell);
                updateChannel(INTELLICHLOR_LOWVOLTAGE, pic.lowvoltage);
                updateChannel(INTELLICHLOR_LOWWATERTEMP, pic.lowwatertemp);
                updateChannel(INTELLICHLOR_COMMERROR, pic.commerror);

                logger.debug("IntelliChlor status: {}", pic.toString());

        }
    }
}
