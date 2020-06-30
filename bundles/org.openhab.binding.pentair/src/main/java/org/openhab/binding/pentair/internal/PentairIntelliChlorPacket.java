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

/**
 * Generic class for the standard pentair package protocol. Includes helpers to generate checksum and extract key bytes
 * from packet.
 *
 * @author Jeff James - initial contribution
 *
 */
public class PentairIntelliChlorPacket extends PentairPacket {

    protected static final int DEST = 2;
    public static final int ACTION = 3;

    // Set Generate %
    protected static final int SALTOUTPUT = 4;

    // Response to set Generate %
    protected static final int SALINITY = 4;
    protected static final int STATUS = 5;

    // Response to get version
    protected static final int VERSION = 4;
    protected static final int NAME = 5;

    public static int getPacketDataLength(int command) {
        int length = -1;

        switch (command) {
            case 0x03: // Response to version
                length = 17;
                break;
            case 0x00: // Get status of Chlorinator
            case 0x11:
            case 0x14:
                length = 1;
                break;
            case 0x01: // Response to Get Status
            case 0x12:
                length = 2;
                break;
        }

        return length;
    }

    public PentairIntelliChlorPacket(byte[] buf, int length) {
        super(buf, length);
    }

    @Override
    public int getAction() {
        return buf[ACTION] & 0xFF;
    }

    public int getVersion() {
        if (this.getAction() != 0x03) {
            return -1;
        }

        return buf[VERSION] & 0xFF;
    }

    public String getName() {
        if (this.getAction() != 0x03) {
            return "";
        }

        String name = new String(buf, NAME, 16);

        return name;
    }

    public int getSaltOutput() {
        if (this.getAction() != 0x11) {
            return -1;
        }

        return buf[SALTOUTPUT] & 0xFF;
    }

    public int getSalinity() {
        if (this.getAction() != 0x12) {
            return -1;
        }

        return (buf[SALINITY] & 0xFF) * 50;
    }

    public boolean getOk() {
        if (this.getAction() != 0x12) {
            return false;
        }

        return ((buf[STATUS] & 0xFF) == 0) || ((buf[STATUS] & 0xFF) == 0x80);
    }

    public boolean getLowFlow() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x01) != 0;
    }

    public boolean getLowSalt() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x02) != 0;
    }

    public boolean getVeryLowSalt() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x04) != 0;
    }

    public boolean getHighCurrent() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x08) != 0;
    }

    public boolean getCleanCell() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x10) != 0;
    }

    public boolean getLowVoltage() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x20) != 0;
    }

    public boolean getLowWaterTemp() {
        if (this.getAction() != 0x12) {
            return false;
        }
        return (buf[STATUS] & 0x40) != 0;
    }
}
