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
package org.openhab.binding.pentair.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the thread to read and parse the input stream. Once a packet can be indentified, it locates the
 * representive sending Thing and dispositions the packet so it can be further processed.
 *
 * @author Jeff James - initial implementation
 *
 */
@NonNullByDefault
public class PentairParser implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(PentairParser.class);

    private enum ParserState {
        WAIT_SOC,
        CMD_PENTAIR,
        CMD_INTELLICHLOR
    };

    private Optional<InputStream> reader = Optional.empty();

    public void setInputStream(InputStream reader) {
        this.reader = Optional.of(reader);
    }

    /**
     * Callback interface when a packet is recieved
     *
     * @author Jeff
     *
     */

    public interface CallbackPentairParser {
        public void onPentairPacket(PentairPacket p);

        public void onIntelliChlorPacket(PentairPacket p);
    };

    private Optional<CallbackPentairParser> callback = Optional.empty();

    public void setCallback(CallbackPentairParser cb) {
        callback = Optional.of(cb);
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

    @Override
    public void run() {
        logger.info("parser thread started");
        byte buf[] = new byte[60];
        int c;
        int chksum, i, length;
        boolean endloop = false;

        ParserState parserstate = ParserState.WAIT_SOC;

        if (!reader.isPresent()) {
            logger.error("PentairParser: no read stream available");
            return;
        }

        while (!Thread.interrupted() && !endloop) {
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
                            logger.info("FF00 !FF");
                            break;
                        }

                        if (getBytes(buf, 0, 6) != 6) { // read enough to get the length
                            logger.info("Unable to read 6 bytes");

                            break;
                        }
                        if (buf[0] != (byte) 0xA5) {
                            logger.info("FF00FF !A5");
                            break;
                        }

                        length = (buf[5] & 0xFF);
                        if (length == 0) {
                            logger.info("Command length of 0");
                        }
                        if (length > 50) {
                            logger.info("Received packet longer than 50 bytes: {}", length);
                            break;
                        }
                        if (getBytes(buf, 6, length) != length) { // read remaining packet
                            break;
                        }

                        chksum = 0;
                        for (i = 0; i < length + 6; i++) {
                            chksum += buf[i] & 0xFF;
                        }

                        c = getByte(parserstate) << 8;
                        c += getByte(parserstate);

                        if (c != chksum) {
                            logger.info("Checksum error: {}-{}", chksum, PentairPacket.bytesToHex(buf, length + 6));
                            break;
                        }

                        PentairPacket p = new PentairPacket(buf, length + 6);

                        logger.debug("PentairPacket: {}", p.toString());
                        callback.get().onPentairPacket(p);

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

                        callback.get().onIntelliChlorPacket(pic);

                        break;
                }
            } catch (IOException e) {
                logger.info("I/O error while reading from stream: {}", e.getMessage());
                endloop = true;
                // TODO _disconnect();
            } catch (EOBException e) {
                logger.trace("EOB Exception: {}", e.toString());
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e1) {
                    logger.info("interrupted exception: {}", Thread.interrupted());
                    endloop = true;
                }
                // EOB Exception is used to exit the parser loop if full message is not in buffer. This is non-fatal
                // and loop should continue
            } catch (Exception e) {
                logger.info("Parser: Exception: {}", e.toString());
                // Assume non-fatal and continue the loop
            }
        }

        logger.debug("msg reader thread exited");
    }
}
