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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Pentair Intellichlor specialation of a PentairPacket. Includes public variables for many of the reverse engineered
 * packet content. Note, Intellichlor packet is of a different format and all helper functions in the base PentairPacket
 * may not apply.
 *
 * This packet can be a 3 or 4 data byte packet.
 *
 * 10 02 50 00 00 62 10 03
 * 10 02 00 01 00 00 13 10 03
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairIntelliChlor {
    protected static final int DEST = 2;
    public static final int ACTION = 3;

    // Set Generate %
    protected static final int SALTOUTPUT = 4;

    // Response to set Generate %
    protected static final int SALINITY = 4;
    protected static final int STATUS = 5;

    /** length of the packet - 3 or 4 data bytes */
    protected int length;
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

    public PentairIntelliChlor() {
        super();
    }

    public void parsePacket(PentairPacket p) {
        switch (p.buf[ACTION]) {
            case 0x11: // set Generate %
                saltoutput = (p.buf[SALTOUTPUT] & 0xFF);
                break;
            case 0x12:
                salinity = (p.buf[SALINITY] & 0xFF);

                ok = (p.buf[STATUS] == 0) || (p.buf[STATUS] == 0x80);
                lowflow = (p.buf[STATUS] & 0x01) != 0;
                lowsalt = (p.buf[STATUS] & 0x02) != 0;
                verylowsalt = (p.buf[STATUS] & 0x04) != 0;
                highcurrent = (p.buf[STATUS] & 0x08) != 0;
                cleancell = (p.buf[STATUS] & 0x10) != 0;
                lowvoltage = (p.buf[STATUS] & 0x20) != 0;
                lowwatertemp = (p.buf[STATUS] & 0x40) != 0;
        }
    }

    @Override
    public String toString() {
        return String.format(
                "saltoutput = %d, salinity = %d, ok = %b, lowflow = %b, lowsalt = %b, verylowsalt = %b, highcurrent = %b, cleancell = %b, lowvoltage = %b, lowwatertemp = %b",
                saltoutput, salinity, ok, lowflow, lowsalt, verylowsalt, highcurrent, cleancell, lowvoltage,
                lowwatertemp);
    }
}
