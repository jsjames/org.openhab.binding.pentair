/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pentair.internal.handler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.pentair.internal.config.PentairSerialBridgeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

/**
 * Handler for the IPBridge. Implements the connect and disconnect abstract methods of {@link PentairBaseBridgeHandler}
 *
 * @author Jeff James - initial contribution
 *
 */
public class PentairSerialBridgeHandler extends PentairBaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairSerialBridgeHandler.class);

    /** SerialPort object representing the port where the RS485 adapter is connected */
    SerialPort port;

    public PentairSerialBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    protected synchronized int connect() {
        PentairSerialBridgeConfig configuration = getConfigAs(PentairSerialBridgeConfig.class);

        try {
            logger.debug("Opening port: {}", configuration.serialPort);

            CommPortIdentifier ci = CommPortIdentifier.getPortIdentifier(configuration.serialPort);

            CommPort cp = ci.open("openhabpentairbridge", 10000);
            if (cp == null) {
                throw new IllegalStateException("cannot open serial port!");
            }

            if (cp instanceof SerialPort) {
                port = (SerialPort) cp;
            } else {
                throw new IllegalStateException("unknown port type");
            }

            logger.debug("setting serial port parameters", configuration.serialPort);
            port.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            port.disableReceiveFraming();
            port.disableReceiveThreshold();

            InputStream is = port.getInputStream();
            if (is == null) {
                throw new Exception("Unable to getInputStream");
            }
            reader = new BufferedInputStream(port.getInputStream());

            OutputStream os = port.getOutputStream();
            if (os == null) {
                throw new Exception("Unable to getOutputStream");
            }
            writer = new BufferedOutputStream(port.getOutputStream());

        } catch (PortInUseException e) {
            String msg = String.format("cannot open serial port: %s", configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -1;
        } catch (UnsupportedCommOperationException e) {
            String msg = String.format("got unsupported operation %s on port %s", e.getMessage(),
                    configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -2;
        } catch (NoSuchPortException e) {
            String msg = String.format("got no such port for %s", configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -3;
        } catch (IllegalStateException e) {
            String msg = String.format("receive IllegalStateException for port %s", configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -4;
        } catch (IOException e) {
            String msg = String.format("IOException on port %s", configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -5;
        } catch (Exception e) {
            String msg = String.format("Excpetion on port %s", configuration.serialPort);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return -6;
        }

        // if you have gotten this far, you should be connected to the serial port
        logger.info("Pentair Bridge connected to serial port: {}", configuration.serialPort);

        parser = new Parser();
        thread = new Thread(parser);
        thread.start();

        updateStatus(ThingStatus.ONLINE);
        return 0;
    }

    @Override
    protected synchronized void disconnect() {
        updateStatus(ThingStatus.OFFLINE);

        if (thread != null) {
            try {
                thread.interrupt();
                thread.join(); // wait for thread to complete
            } catch (InterruptedException e) {
                // do nothing
            }
            thread = null;
            parser = null;
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                logger.trace("IOException when closing serial reader: {}", e);
            }
            reader = null;
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                logger.trace("IOException when closing serial writer: {}", e);
            }
            writer = null;
        }

        if (port != null) {
            port.close();
            port = null;
        }
    }
}
