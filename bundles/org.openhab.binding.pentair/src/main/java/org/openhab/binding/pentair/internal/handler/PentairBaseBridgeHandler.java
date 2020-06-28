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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.pentair.internal.PentairDiscoveryService;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.PentairParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for all common functions for different bridge implementations. Use as superclass for IPBridge and
 * SerialBridge implementations.
 *
 * - Implements parsing of packets on Pentair bus and dispositions to appropriate Thing
 * - Periodically sends query to any {@link PentairIntelliFloHandler} things
 * - Provides function to write packets
 *
 * @author Jeff James - Initial contribution
 *
 */
@NonNullByDefault
public abstract class PentairBaseBridgeHandler extends BaseBridgeHandler
        implements PentairParser.CallbackPentairParser {
    private final Logger logger = LoggerFactory.getLogger(PentairBaseBridgeHandler.class);

    /** input stream - subclass needs to assign in connect function */
    protected Optional<BufferedInputStream> reader = Optional.empty();
    /** output stream - subclass needs to assign in connect function */
    protected Optional<BufferedOutputStream> writer = Optional.empty();

    protected PentairParser parser = new PentairParser();

    /** thread for parser - subclass needs to create/assign connect */
    private @Nullable Thread thread;
    /** polling job for reconnecting */
    protected @Nullable ScheduledFuture<?> pollingjob;
    /** ID to use when sending commands on Pentair bus - subclass needs to assign based on configuration parameter */
    protected int id;
    /** array to keep track of IDs seen on the Pentair bus that are not configured yet */
    protected ArrayList<Integer> unregistered = new ArrayList<>();

    protected Map<Integer, @Nullable PentairBaseThingHandler> equipment = new HashMap<Integer, @Nullable PentairBaseThingHandler>();

    protected ConnectState connectstate = ConnectState.INIT;

    private ReentrantLock lock = new ReentrantLock();
    private Condition waitAck = lock.newCondition();
    private int ackResponse = -1;

    protected boolean discovery = false;

    protected @Nullable PentairDiscoveryService discoveryService;

    public void setDiscoveryService(PentairDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Gets pentair bus id
     *
     * @return id
     */
    public int getId() {
        return id;
    }

    protected enum ConnectState {
        CONNECTING,
        DISCONNECTED,
        CONNECTED,
        INIT,
        CONFIGERROR
    };

    /**
     * Constructor
     *
     * @param bridge
     */
    PentairBaseBridgeHandler(Bridge bridge) {
        super(bridge);
        parser.setCallback(this);
        connectstate = ConnectState.INIT;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.debug("Bridge received refresh command");
        }
    }

    @Override
    public void initialize() {
        logger.debug("initializing Pentair Bridge handler.");

        _connect();

        return;
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposed.");

        _disconnect();

        if (pollingjob != null) {
            pollingjob.cancel(true);
        }
    }

    /**
     * Abstract method for creating connection. Must be implemented in subclass.
     * Return 0 if all goes well. Must call setInputStream and setOutputStream before exciting.
     *
     * @throws Exception
     */
    protected abstract int connect();

    private int _connect() {
        if (connectstate != ConnectState.DISCONNECTED && connectstate != ConnectState.INIT) {
            logger.debug("_connect() without ConnectState == DISCONNECTED or INIT: {}", connectstate);
        }

        connectstate = ConnectState.CONNECTING;

        if (connect() != 0) {
            connectstate = ConnectState.CONFIGERROR;

            return -1;
        }

        connectstate = ConnectState.CONNECTED;

        if (pollingjob == null) {
            pollingjob = scheduler.scheduleWithFixedDelay(new ReconnectIO(), 60, 30, TimeUnit.SECONDS);
        }

        thread = new Thread(parser);
        thread.start();

        if (reader.isPresent() && writer.isPresent()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            logger.debug("connect: reader or writer is null");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Unable to connect");
        }

        return 0;
    }

    public void setInputStream(InputStream inputStream) {
        reader.ifPresent(close -> {
            try {
                reader.get().close();
            } catch (IOException e) {
                logger.debug("setInputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        reader = Optional.of(new BufferedInputStream(inputStream));
        parser.setInputStream(inputStream);
    }

    public void setOutputStream(OutputStream outputStream) {
        writer.ifPresent(close -> {
            try {
                writer.get().close();
            } catch (IOException e) {
                logger.debug("setOutputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        writer = Optional.of(new BufferedOutputStream(outputStream));
    }

    /**
     * Abstract method for disconnect. Must be implemented in subclass
     */
    protected abstract void disconnect();

    private void _disconnect() {
        if (thread != null) {
            try {
                thread.interrupt();
                thread.join(); // wait for thread to complete
            } catch (InterruptedException e) {
                // do nothing
            }
            thread = null;
        }

        reader.ifPresent(close -> {
            try {
                reader.get().close();
            } catch (IOException e) {
                logger.debug("setInputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        writer.ifPresent(close -> {
            try {
                writer.get().close();
            } catch (IOException e) {
                logger.debug("setOutputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        disconnect();

        connectstate = ConnectState.DISCONNECTED;
    }

    // Job to pull to try and reconnect upon being disconnected. Note this should only be started on an initial
    class ReconnectIO implements Runnable {
        @Override
        public void run() {
            logger.debug("ReconnectIO:run");
            if (connectstate == ConnectState.DISCONNECTED) {
                _connect();
            }
        }
    }

    /**
     * Helper function to find a Thing assigned to this bridge with a specific pentair bus id.
     *
     * @param id Pentair bus id
     * @return Thing object. null if id is not found.
     */
    public @Nullable Thing findThing(int id) {
        List<Thing> things = getThing().getThings();

        for (Thing t : things) {
            PentairBaseThingHandler handler = (PentairBaseThingHandler) t.getHandler();

            if (handler != null && handler.getPentairID() == id) {
                return t;
            }
        }

        return null;
    }

    @Override
    public void onPentairPacket(PentairPacket p) {
        @Nullable
        PentairBaseThingHandler thinghandler;

        thinghandler = equipment.get(p.getSource());

        if (thinghandler == null) {
            int source = p.getSource();

            if ((source >> 4) == 0x02) { // control panels are 0x2*, don't treat as an
                                         // unregistered device
                logger.debug("Command from control panel device ({}): {}", p.getSource(), p);
            } else if (!unregistered.contains(p.getSource())) { // if not yet seen, print out log
                                                                // message once
                if ((source >> 4) == 0x01) { // controller
                    if (PentairControllerHandler.onlineController == null) { // only register one
                                                                             // controller
                        if (discovery && discoveryService != null) {
                            discoveryService.notifyDiscoveredController(source);
                        }
                    }
                } else if ((source >> 4) == 0x06) {
                    if (discovery && discoveryService != null) {
                        discoveryService.notifyDiscoverdIntelliflo(source);
                    }
                } else if ((source >> 4) == 0x09) {
                    if (discovery && discoveryService != null) {
                        discoveryService.notifyDiscoveryIntellichem(source);
                    }
                }

                logger.info("Command from unregistered device ({}): {}", p.getSource(), p);
                unregistered.add(p.getSource());

            } else {
                logger.debug("Command from unregistered device ({}): {}", p.getSource(), p);
            }
        } else {
            logger.debug("Received pentair command: {}", p);

            thinghandler.processPacketFrom(p);
            ackResponse(p.getAction());
        }
    }

    @Override
    public void onIntelliChlorPacket(PentairPacket p) {
        @Nullable
        PentairBaseThingHandler thinghandler;

        thinghandler = equipment.get(0);

        if (thinghandler == null) {
            if (!unregistered.contains(0)) { // if not yet seen, print out log message
                if (discovery && discoveryService != null) {
                    discoveryService.notifyDiscoveredIntellichlor(0);
                }
                logger.info("Command from unregistered Intelliflow: {}", p);
                unregistered.add(0);
            } else {
                logger.trace("Command from unregistered Intelliflow: {}", p);
            }

            return;
        }

        thinghandler.processPacketFrom(p);
    }

    /**
     * Method to write a package on the Pentair bus. Will add preamble and checksum to bytes written
     *
     * @param p {@link PentairPacket} to write
     */
    public void writePacket(PentairPacket p) {
        writePacket(p, -1, 0);
    }

    public boolean writePacket(PentairPacket p, int response, int retries) {
        boolean bReturn = true;

        try {
            byte[] buf;
            int nRetries = retries;

            if (!writer.isPresent()) {
                logger.debug("writePacket: writer = null");
                return false;
            }
            p.setSource(id);

            buf = p.getFullWriteStream();

            lock.lock();
            ackResponse = response;

            do {
                logger.debug("Writing packet: {}", PentairPacket.bytesToHex(buf));

                writer.get().write(buf, 0, buf.length);
                writer.get().flush();

                if (response != -1) {
                    logger.debug("writePacket: wait for ack (response: {}, retries: {})", response, nRetries);
                    bReturn = waitAck.await(1000, TimeUnit.MILLISECONDS); // bReturn will be false if timeout
                    nRetries--;
                }
            } while ((bReturn != true) && (nRetries >= 0));
        } catch (IOException e) {
            logger.debug("I/O error while writing stream: {}", e.getMessage());
            _disconnect();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            logger.debug("writePacket: InterruptedException: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("writePacket: Exception: {}", e.getMessage());
        } finally {
            lock.unlock();
        }

        if (!bReturn) {
            logger.debug("writePacket: timeout");
        }
        return bReturn;
    }

    /**
     * Method to acknowledge an ack or response packet has been sent
     *
     * @param response is the command that was seen as a return. This is validate against that this was the response
     *            before signally a return.
     * @param retries is the number of retries if an ack is not seen before timeout
     * @return true if writePacket was successful and if required saw a response
     */
    public boolean writePacket2(PentairPacket p, int response, int retries) {
        boolean bReturn = true;

        try { // FF 00 FF A5 00 60 10 07 00 01 1C
              // byte[] preamble = { (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF };
            byte[] preamble = { (byte) 0xFF, (byte) 0x00, (byte) 0xFF };
            byte[] buf = new byte[5 + p.getLength() + 8]; // 5 is preamble, 8 is 6 bytes for header and 2 for checksum
            int nRetries = retries;

            if (!writer.isPresent()) {
                logger.debug("writePacket: writer = null");
                return false;
            }
            p.setSource(id);

            System.arraycopy(preamble, 0, buf, 0, preamble.length);
            System.arraycopy(p.buf, 0, buf, preamble.length, p.getLength() + 6);
            int checksum = p.calcChecksum();

            buf[p.getLength() + preamble.length + 6] = (byte) ((checksum >> 8) & 0xFF);
            buf[p.getLength() + preamble.length + 7] = (byte) (checksum & 0xFF);

            lock.lock();
            ackResponse = response;

            do {
                logger.debug("Writing packet: {}", PentairPacket.bytesToHex(buf));

                writer.get().write(buf, 0, preamble.length + p.getLength() + 8);
                writer.get().flush();

                if (response != -1) {
                    logger.debug("writePacket: wait for ack (response: {}, retries: {})", response, nRetries);
                    bReturn = waitAck.await(1000, TimeUnit.MILLISECONDS); // bReturn will be false if timeout
                    nRetries--;
                }
            } while ((bReturn != true) && (nRetries >= 0));
        } catch (IOException e) {
            logger.debug("I/O error while writing stream: {}", e.getMessage());
            _disconnect();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            logger.debug("writePacket: InterruptedException: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("writePacket: Exception: {}", e.getMessage());
        } finally {
            lock.unlock();
        }

        if (!bReturn) {
            logger.debug("writePacket: timeout");
        }
        return bReturn;
    }

    /**
     * Method to acknowledge an ack or response packet has been sent
     *
     * @param cmdresponse is the command that was seen as a return. This is validate against that this was the response
     *            before signally a return.
     */
    public void ackResponse(int response) {
        if (response != ackResponse) {
            return;
        }

        try {
            lock.lock();
            waitAck.signalAll();
            lock.unlock();
        } catch (Exception e) {
            logger.debug("ackResponse: Exception: {}", e.getMessage());
        }
    }
}
