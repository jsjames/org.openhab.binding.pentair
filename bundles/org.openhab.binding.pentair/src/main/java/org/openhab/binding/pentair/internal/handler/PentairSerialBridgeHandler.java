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
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.io.transport.serial.PortInUseException;
import org.eclipse.smarthome.io.transport.serial.SerialPort;
import org.eclipse.smarthome.io.transport.serial.SerialPortIdentifier;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.eclipse.smarthome.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.binding.pentair.internal.config.PentairSerialBridgeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the IPBridge. Implements the connect and disconnect abstract methods of {@link PentairBaseBridgeHandler}
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairSerialBridgeHandler extends PentairBaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairSerialBridgeHandler.class);

    public PentairSerialBridgeConfig config = new PentairSerialBridgeConfig();
    /** SerialPort object representing the port where the RS485 adapter is connected */
    @Nullable
    private final SerialPortManager serialPortManager;
    private @Nullable SerialPort port;
    private @Nullable SerialPortIdentifier portIdentifier;

    public PentairSerialBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);
        this.serialPortManager = serialPortManager;
    }

    @Override
    protected synchronized int connect() {
        logger.debug("PentairSerialBridgeHander: connect");
        config = getConfigAs(PentairSerialBridgeConfig.class);

        if (config.serialPort.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "no serial port configured");
            return -1;
        }

        this.id = config.id;
        logger.debug("Serial port id: {}", id);
        this.discovery = config.discovery;

        portIdentifier = serialPortManager.getIdentifier(config.serialPort);
        if (portIdentifier == null) {
            logger.debug("Serial Error: Port {} does not exist.", config.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Configured serial port does not exist");
            return -1;
        }

        try {
            logger.debug("connect port: {}", config.serialPort);

            if (portIdentifier.isCurrentlyOwned()) {
                logger.error("Serial port is currently being used by another application {}",
                        portIdentifier.getCurrentOwner());
                // for debug purposes, will continue to try and open
            }

            port = portIdentifier.open("org.openhab.binding.pentair", 10000);
            port.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

            // Note: The V1 code called disableReceiveFraming() and disableReceiveThreshold() here
            // port.disableReceiveFraming();
            // port.disableReceiveThreshold();

            InputStream is = port.getInputStream();
            if (is == null) {
                logger.error("Unable to get write access on port {}", config.serialPort);
            }

            this.setReader(new BufferedInputStream(is));

            OutputStream os = port.getOutputStream();
            if (os == null) {
                logger.error("Unable to get write access on port {}", config.serialPort);
            }

            this.setWriter(new BufferedOutputStream(os));
        } catch (PortInUseException e) {
            String msg = String.format("Serial port already in use: %s", config.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -1;
        } catch (UnsupportedCommOperationException e) {
            String msg = String.format("got unsupported operation %s on port %s", e.getMessage(), config.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -2;
        } catch (IOException e) {
            String msg = String.format("got IOException %s on port %s", e.getMessage(), config.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -2;
        }

        // if you have gotten this far, you should be connected to the serial port
        logger.info("Pentair Bridge connected to serial port: {}", config.serialPort);

        parser = new Parser();
        thread = new Thread(parser);
        thread.start();

        updateStatus(ThingStatus.ONLINE);

        return 0;
    }

    @Override
    protected synchronized void disconnect() {
        logger.debug("PentairSerialBridgeHandler: disconnect");
        updateStatus(ThingStatus.OFFLINE);

        if (thread != null) {
            try {
                thread.interrupt();
                if (thread != null) {
                    thread.join(); // wait for thread to complete
                }
            } catch (InterruptedException e) {
                // do nothing
            }
            thread = null;
            parser = null;
        }

        if (reader.isPresent()) {
            try {
                logger.debug("Closing reader buffer");
                reader.get().close();
            } catch (IOException e) {
                logger.trace("IOException when closing serial reader: {}", e.toString());
            }
            reader = Optional.empty();
        }

        if (writer.isPresent()) {
            try {
                logger.debug("Closing writer buffer");
                writer.get().close();
            } catch (IOException e) {
                logger.trace("IOException when closing serial writer: {}", e.toString());
            }
            writer = Optional.empty();
        }

        if (port != null) {
            logger.debug("Closing serial port");
            port.close();
            port = null;
        }
    }
}
