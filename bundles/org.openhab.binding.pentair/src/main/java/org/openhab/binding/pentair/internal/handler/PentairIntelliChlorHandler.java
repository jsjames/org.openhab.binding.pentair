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
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.pentair.internal.PentairBindingConstants;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.PentairPacketIntellichlor;
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

    protected PentairPacketIntellichlor pic3cur = new PentairPacketIntellichlor();
    protected PentairPacketIntellichlor pic4cur = new PentairPacketIntellichlor();

    public PentairIntelliChlorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing IntelliChlor - Thing ID: {}.", this.getThing().getUID());

        id = 0; // Intellichlor doesn't have ID

        goOnline();
    }

    @Override
    public void dispose() {
        logger.debug("Thing {} disposed.", getThing().getUID());

        goOffline(ThingStatusDetail.NONE);
    }

    public void goOnline() {
        logger.debug("Thing {} goOnline.", getThing().getUID());

        waitStatusForOnline = true;
    }

    public void goOffline(ThingStatusDetail detail) {
        logger.debug("Thing {} goOffline.", getThing().getUID());

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
            updateChannel(channelUID.getId(), null);
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {
        PentairPacketIntellichlor pic = (PentairPacketIntellichlor) p;

        if (waitStatusForOnline) {
            updateStatus(ThingStatus.ONLINE);
            waitStatusForOnline = false;
        }

        switch (pic.getLength()) {
            case 3:
                if (pic.getCmd() != 0x11) { // only packets with 0x11 have valid saltoutput numbers.
                    break;
                }

                PentairPacketIntellichlor pic3Old = pic3cur;
                pic3cur = pic;

                updateChannel(INTELLICHLOR_SALTOUTPUT, pic3Old);

                break;
            case 4:
                if (pic.getCmd() != 0x12) {
                    break;
                }

                PentairPacketIntellichlor pic4Old = pic4cur;
                pic4cur = pic;

                updateChannel(INTELLICHLOR_SALINITY, pic4Old);

                break;
        }

        logger.debug("Intellichlor command: {}", pic);
    }

    /**
     * Helper function to compare and update channel if needed. The class variables p29_cur and phsp_cur are used to
     * determine the appropriate state of the channel.
     *
     * @param channel name of channel to be updated, corresponds to channel name in {@link PentairBindingConstants}
     * @param p Packet representing the former state. If null, no compare is done and state is updated.
     */
    public void updateChannel(String channel, @Nullable PentairPacket p) {
        PentairPacketIntellichlor pic = (PentairPacketIntellichlor) p;

        switch (channel) {
            case INTELLICHLOR_SALINITY:
                if (pic == null || (pic.salinity != pic4cur.salinity)) {
                    updateState(channel, new DecimalType(pic4cur.salinity));
                }
                break;
            case INTELLICHLOR_SALTOUTPUT:
                if (pic == null || (pic.saltoutput != pic3cur.saltoutput)) {
                    updateState(channel, new DecimalType(pic3cur.saltoutput));
                }
                break;
        }
    }
}
