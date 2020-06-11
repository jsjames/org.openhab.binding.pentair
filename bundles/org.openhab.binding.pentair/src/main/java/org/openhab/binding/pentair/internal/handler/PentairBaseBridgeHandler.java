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
public abstract class PentairBaseBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairBaseBridgeHandler.class);

    /** input stream - subclass needs to assign in connect function */
    protected Optional<BufferedInputStream> reader = Optional.empty();
    /** output stream - subclass needs to assign in connect function */
    protected Optional<BufferedOutputStream> writer = Optional.empty();
    /** thread for parser - subclass needs to create/assign connect */
    protected @Nullable Thread thread;
    /** parser object - subclass needs to create/assign during connect */
    protected @Nullable Parser parser;
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

    private enum ParserState {
        WAIT_SOC,
        CMD_PENTAIR,
        CMD_INTELLICHLOR
    };

    /**
     * Constructor
     *
     * @param bridge
     */
    PentairBaseBridgeHandler(Bridge bridge) {
        super(bridge);
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
     * Return 0 if all goes well.
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

        return 0;
    }

    /**
     * Abstract method for disconnect. Must be implemented in subclass
     */
    protected abstract void disconnect();

    private void _disconnect() {
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

    /**
     * Class for throwing an End of Buffer exception, used in getByte when read returns a -1. This is used to signal an
     * exit from the parser.
     *
     * @author Jeff James - initial contribution
     *
     */
    public class EOBException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Gets a single byte from reader input stream
     *
     * @param s used during debug to identify proper state transitioning
     * @return next byte from reader
     * @throws EOBException
     * @throws IOException
     */
    private int getByte(ParserState s) throws EOBException, IOException {
        int c = 0;

        if (reader.isPresent()) {
            c = reader.get().read();
        } else {
            c = -1;
        }

        if (c == -1) {
            // EOBException is thrown if no more bytes in buffer. This exception is used to exit the parser when full
            // packet is not in buffer
            throw new EOBException();
        }

        return c;
    }

    /**
     * Gets a specific number of bytes from reader input stream
     *
     * @param buf byte buffer to store bytes
     * @param start starting index to store bytes
     * @param n number of bytes to read
     * @return number of bytes read
     * @throws EOBException
     * @throws IOException
     */
    private int getBytes(byte[] buf, int start, int n) throws EOBException, IOException {
        int i = 0;
        ;
        int c;

        if (reader.isPresent()) {

            for (i = 0; i < n; i++) {
                c = reader.get().read();
                if (c == -1) {
                    // EOBException is thrown if no more bytes in buffer. This exception is used to exit the parser when
                    // full packet is not in buffer
                    throw new EOBException();
                }

                buf[start + i] = (byte) c;
            }
        }

        return i;
    }

    /**
     * Implements the thread to read and parse the input stream. Once a packet can be indentified, it locates the
     * representive sending Thing and dispositions the packet so it can be further processed.
     *
     * @author Jeff James - initial implementation
     *
     */
    class Parser implements Runnable {
        @Override
        public void run() {
            logger.debug("parser thread started");
            byte buf[] = new byte[60];
            int c;
            int chksum, i, length;
            boolean endloop = false;
            @Nullable
            PentairBaseThingHandler thinghandler;
            ParserState parserstate = ParserState.WAIT_SOC;

            while (!Thread.currentThread().isInterrupted() && !endloop) {
                try {
                    c = getByte(parserstate);

                    switch (parserstate) {
                        case WAIT_SOC:
                            if (c == 0xFF) { // for CMD_PENTAIR, we need at lease one 0xFF
                                do {
                                    c = getByte(parserstate);
                                } while (c == 0xFF); // consume all 0xFF

                                if (c == 0x00) {
                                    parserstate = ParserState.CMD_PENTAIR;
                                }
                            }

                            if (c == 0x10) {
                                parserstate = ParserState.CMD_INTELLICHLOR;
                            }
                            break;
                        case CMD_PENTAIR:
                            parserstate = ParserState.WAIT_SOC; // any break will go back to WAIT_SOC

                            if (c != 0xFF) {
                                logger.debug("FF00 !FF");
                                break;
                            }

                            if (getBytes(buf, 0, 6) != 6) { // read enough to get the length
                                logger.debug("Unable to read 6 bytes");

                                break;
                            }
                            if (buf[0] != (byte) 0xA5) {
                                logger.debug("FF00FF !A5");
                                break;
                            }

                            length = (buf[5] & 0xFF);
                            if (length == 0) {
                                logger.debug("Command length of 0");
                            }
                            if (length > 50) {
                                logger.debug("Received packet longer than 50 bytes: {}", length);
                                break;
                            }
                            if (getBytes(buf, 6, length) != length) { // read remaining packet
                                break;
                            }

                            /*
                             * temp++;
                             * if ((temp % 15) == 0) {
                             * System.arraycopy(ichem, 0, buf, 0, ichem.length);
                             * }
                             */

                            chksum = 0;
                            for (i = 0; i < length + 6; i++) {
                                chksum += buf[i] & 0xFF;
                            }

                            c = getByte(parserstate) << 8;
                            c += getByte(parserstate);

                            if (c != chksum) {
                                logger.debug("Checksum error: {}", PentairPacket.bytesToHex(buf, length + 6));
                                break;
                            }

                            PentairPacket p = new PentairPacket(buf);

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
                                break;
                            }

                            thinghandler = (PentairBaseThingHandler) thing.getHandler();
                            if (thinghandler == null) {
                                logger.debug("Thing handler = null");
                                break;
                            }

                            logger.debug("Received pentair command: {}", p);

                            thinghandler.processPacketFrom(p);
                            ackResponse(p.getAction());

                            break;
                        case CMD_INTELLICHLOR:
                            parserstate = ParserState.WAIT_SOC;

                            buf[0] = 0x10; // 0x10 is included in checksum
                            if (c != (byte) 0x02) {
                                break;
                            }

                            buf[1] = 0x2;
                            length = 3;
                            // assume 3 byte command, plus 1 checksum, plus 0x10, 0x03
                            if (getBytes(buf, 2, 6) != 6) {
                                break;
                            }

                            // Check to see if this is a 3 or 4 byte command
                            if ((buf[6] != (byte) 0x10 || buf[7] != (byte) 0x03)) {
                                length = 4;

                                buf[8] = (byte) getByte(parserstate);
                                if ((buf[7] != (byte) 0x10) && (buf[8] != (byte) 0x03)) {
                                    logger.debug("Invalid Intellichlor command: {}",
                                            PentairPacket.bytesToHex(buf, length + 6));
                                    break; // invalid command
                                }
                            }

                            chksum = 0;
                            for (i = 0; i < length + 2; i++) {
                                chksum += buf[i] & 0xFF;
                            }

                            c = buf[length + 2] & 0xFF;
                            if (c != (chksum & 0xFF)) { // make sure it matches chksum
                                logger.debug("Invalid Intellichlor checksum: {}",
                                        PentairPacket.bytesToHex(buf, length + 6));
                                break;
                            }

                            PentairPacket pic = new PentairPacket(buf, length);

                            thinghandler = equipment.get(0);

                            if (thinghandler == null) {
                                if (!unregistered.contains(0)) { // if not yet seen, print out log message
                                    if (discovery) {
                                        discoveryService.notifyDiscoveredIntellichlor(0);
                                    }
                                    logger.info("Command from unregistered Intelliflow: {}", pic);
                                    unregistered.add(0);
                                } else {
                                    logger.trace("Command from unregistered Intelliflow: {}", pic);
                                }

                                break;
                            }

                            thinghandler = (PentairBaseThingHandler) thing.getHandler();
                            if (thinghandler == null) {
                                logger.debug("Thing handler = null");
                                break;
                            }

                            thinghandler.processPacketFrom(pic);
                            logger.debug("intelichlor: after processPacketFrom");

                            break;
                    }
                } catch (IOException e) {
                    logger.error("I/O error while reading from stream: {}", e.getMessage());
                    endloop = true;
                    _disconnect();
                } catch (EOBException e) {
                    logger.error("EOB Exception: {}", e.toString());
                    // EOB Exception is used to exit the parser loop if full message is not in buffer. This is non-fatal
                    // and loop should continue
                } catch (Exception e) {
                    logger.error("Parser: Exception: {}", e.toString());
                    // Assume non-fatal and continue the loop
                }
            }

            logger.debug("msg reader thread exited");
        }
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
            int nRetries = retries;
            p.addChecksum();

            lock.lock();
            ackResponse = response;

            do {
                logger.debug("Writing packet: {}", p.toString());

                if (writer.isPresent()) {

                    writer.get().write(p.buf, 0, p.getBufLength());
                    writer.get().flush();
                }

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

    public Optional<BufferedInputStream> getReader() {
        return reader;
    }

    public void setReader(BufferedInputStream reader) {
        this.reader = Optional.of(reader);
    }

    public Optional<BufferedOutputStream> getWriter() {
        return writer;
    }

    public void setWriter(BufferedOutputStream writer) {
        this.writer = Optional.of(writer);
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
            byte[] preamble = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF };
            byte[] buf = new byte[5 + p.getLength() + 8]; // 5 is preamble, 8 is 6 bytes for header and 2 for checksum
            int nRetries = retries;

            if (!writer.isPresent()) {
                logger.debug("writePacket: writer = null");
                return false;
            }
            p.setSource(id);

            System.arraycopy(preamble, 0, buf, 0, 5);
            System.arraycopy(p.buf, 0, buf, 5, p.getLength() + 6);
            System.arraycopy(p.buf, 0, buf, 0, p.getLength());
            int checksum = p.calcChecksum();

            buf[p.getLength() + 11] = (byte) ((checksum >> 8) & 0xFF);
            buf[p.getLength() + 12] = (byte) (checksum & 0xFF);

            lock.lock();
            ackResponse = response;

            do {
                logger.debug("Writing packet: {}", PentairPacket.bytesToHex(buf));

                writer.get().write(buf, 0, 5 + p.getLength() + 8);
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
