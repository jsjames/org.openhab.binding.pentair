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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.pentair.internal.PentairIntelliChlorPacket;
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

    int version;
    String name = "";

    /** for a saltoutput packet, represents the salt output percent */
    public int saltoutput;
    /** for a salinity packet, is value of salinity. Must be multiplied by 50 to get the actual salinity value. */
    public int salinity;

    public boolean ok;
    public boolean lowflow;
    public boolean lowsalt;
    public boolean verylowsalt;
    public boolean highcurrent;
    public boolean cleancell;
    public boolean lowvoltage;
    public boolean lowwatertemp;
    public boolean commerror;

    @Nullable
    public static PentairIntelliChlorHandler onlineChlorinator;

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
                    updateChannel(INTELLICHLOR_SALTOUTPUT, saltoutput);
                    break;
                case INTELLICHLOR_SALINITY:
                    updateChannel(INTELLICHLOR_SALINITY, salinity);
                    break;
                case INTELLICHLOR_OK:
                    updateChannel(INTELLICHLOR_OK, ok);
                    break;
                case INTELLICHLOR_LOWFLOW:
                    updateChannel(INTELLICHLOR_LOWFLOW, lowflow);
                    break;
                case INTELLICHLOR_LOWSALT:
                    updateChannel(INTELLICHLOR_LOWSALT, lowsalt);
                    break;
                case INTELLICHLOR_VERYLOWSALT:
                    updateChannel(INTELLICHLOR_VERYLOWSALT, verylowsalt);
                    break;
                case INTELLICHLOR_HIGHCURRENT:
                    updateChannel(INTELLICHLOR_HIGHCURRENT, highcurrent);
                    break;
                case INTELLICHLOR_CLEANCELL:
                    updateChannel(INTELLICHLOR_CLEANCELL, cleancell);
                    break;
                case INTELLICHLOR_LOWVOLTAGE:
                    updateChannel(INTELLICHLOR_LOWVOLTAGE, lowvoltage);
                    break;
                case INTELLICHLOR_LOWWATERTEMP:
                    updateChannel(INTELLICHLOR_LOWWATERTEMP, lowwatertemp);
                    break;
                case INTELLICHLOR_COMMERROR:
                    updateChannel(INTELLICHLOR_COMMERROR, commerror);
                    break;
            }
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {

        if (waitStatusForOnline) { // Only go online after first response from the Intellichlor
            updateStatus(ThingStatus.ONLINE);
            onlineChlorinator = this;
            waitStatusForOnline = false;
        }

        PentairIntelliChlorPacket pic = (PentairIntelliChlorPacket) p;

        switch (p.getAction()) {
            case 0x03:
                version = pic.getVersion();
                name = pic.getName();

                Map<String, String> editProperties = editProperties();
                editProperties.put(INTELLICHLOR_PROPERTYVERSION, Integer.toString(version));
                editProperties.put(INTELLICHLOR_PROPERTYMODEL, name);
                updateProperties(editProperties);

                logger.debug("Intellichlor version: {}, {}", version, name);
                break;

            case 0x11: // set salt output % command
                saltoutput = pic.getSaltOutput();
                updateChannel(INTELLICHLOR_SALTOUTPUT, saltoutput);
                logger.debug("Intellichlor set output % {}", saltoutput);
                break;
            case 0x12: // response to set salt output
                salinity = pic.getSalinity();

                ok = pic.getOk();
                lowflow = pic.getLowFlow();
                lowsalt = pic.getLowSalt();
                verylowsalt = pic.getVeryLowSalt();
                highcurrent = pic.getHighCurrent();
                cleancell = pic.getCleanCell();
                lowvoltage = pic.getLowVoltage();
                lowwatertemp = pic.getLowWaterTemp();

                updateChannel(INTELLICHLOR_SALINITY, salinity);
                updateChannel(INTELLICHLOR_OK, ok);
                updateChannel(INTELLICHLOR_LOWFLOW, lowflow);
                updateChannel(INTELLICHLOR_LOWSALT, lowsalt);
                updateChannel(INTELLICHLOR_VERYLOWSALT, verylowsalt);
                updateChannel(INTELLICHLOR_HIGHCURRENT, highcurrent);
                updateChannel(INTELLICHLOR_CLEANCELL, cleancell);
                updateChannel(INTELLICHLOR_LOWVOLTAGE, lowvoltage);
                updateChannel(INTELLICHLOR_LOWWATERTEMP, lowwatertemp);

                String status = String.format(
                        "saltoutput = %d, salinity = %d, ok = %b, lowflow = %b, lowsalt = %b, verylowsalt = %b, highcurrent = %b, cleancell = %b, lowvoltage = %b, lowwatertemp = %b",
                        saltoutput, salinity, ok, lowflow, lowsalt, verylowsalt, highcurrent, cleancell, lowvoltage,
                        lowwatertemp);
                logger.debug("IntelliChlor salinity/status: {}, {}", salinity, status);
        }
    }
}
