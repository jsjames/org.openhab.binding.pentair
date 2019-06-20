/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pentair.internal;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for the pentair controller schedules.
 *
 * @author Jeff James - initial contribution
 *
 */
public class PentairPacketControllerSchedule extends PentairPacket {
    protected static final int ID = STARTOFDATA;
    protected static final int CIRCUIT = STARTOFDATA + 1;
    protected static final int STARTH = STARTOFDATA + 2;
    protected static final int STARTM = STARTOFDATA + 3;
    protected static final int ENDH = STARTOFDATA + 4;
    protected static final int ENDM = STARTOFDATA + 5;
    protected static final int DAYS = STARTOFDATA + 6;

    public static final int SCHEDULETYPE_NONE = 0;
    public static final int SCHEDULETYPE_NORMAL = 1;
    public static final int SCHEDULETYPE_EGGTIMER = 2;
    public static final int SCHEDULETYPE_ONCEONLY = 3;

    //@formatter:off
    public static final Map<Integer, String> SCHEDULETYPE = MapUtils.mapOf(
            SCHEDULETYPE_NONE, "NONE",
            SCHEDULETYPE_NORMAL, "NORMAL",
            SCHEDULETYPE_EGGTIMER, "EGGTIMER",
            SCHEDULETYPE_ONCEONLY, "ONCEONLY");
    //@formatter:on

    public int id;
    public int circuit;
    public int type;

    public int starth;
    public int startm;
    public int endh;
    public int endm;

    public int days;

    public PentairPacketControllerSchedule(PentairPacket p) {
        super(p);

        id = buf[ID];
        circuit = buf[CIRCUIT];
        starth = buf[STARTH];
        startm = buf[STARTM];
        endh = buf[ENDH];
        endm = buf[ENDM];
        days = buf[DAYS];

        if (endh == 25) {
            type = SCHEDULETYPE_EGGTIMER;
        } else if (endh == 26) {
            type = SCHEDULETYPE_ONCEONLY;
        } else if (circuit == 0) {
            type = SCHEDULETYPE_NONE;
        } else {
            type = SCHEDULETYPE_NORMAL;
        }
    }

    public PentairPacketControllerSchedule() {
        super();
    }

    public String GetTypeString() {
        String str = SCHEDULETYPE.get(type);

        return str;
    }

    @Override
    public String toString() {
        String dow = "SMTWRFY";
        String str = String.format("%s,%d,%02d:%02d,%02d:%02d,", GetTypeString(), circuit, starth, startm, endh, endm);

        for (int i = 6; i >= 0; i--) {
            if (((days >> i) & 0x01) == 0x01) {
                str += dow.charAt(6 - i);
            }
        }

        return str;
    }

    public boolean ParseString(String str) {
        String dow = "SMTWRFY";
        String schedulestr = str.toUpperCase();

        Pattern ptn = Pattern
                .compile("^(NONE|NORMAL|EGGTIMER|ONCEONLY),(\\d+),(\\d+):(\\d+),(\\d+):(\\d+),([SMTWRFY]+)");
        Matcher m = ptn.matcher(schedulestr);

        if (!m.find()) {
            return false;
        }

        // TODO: validate numbers
        if (m.group(1).equals("NORMAL")) {
            circuit = Integer.parseUnsignedInt(m.group(2));
            if (circuit > 8) {
                return false;
            }

            starth = Integer.parseUnsignedInt(m.group(3));
            if (starth <= 0 || starth >= 25) {
                return false;
            }

            startm = Integer.parseUnsignedInt(m.group(4));
            if (startm < 0 || startm >= 60) {
                return false;
            }

            endh = Integer.parseUnsignedInt(m.group(5));
            if (endh <= 0 || endh >= 25) {
                return false;
            }

            endm = Integer.parseUnsignedInt(m.group(6));
            if (endm < 0 || endm >= 60) {
                return false;
            }

            days = 0;
            String d = m.group(6);
            for (int i = 0; i <= 6; i++) {
                if (d.indexOf(dow.charAt(i)) >= 0) {
                    days |= 0x01 << (6 - i);
                }
            }

        } else if (m.group(1) == "NONE") {
            circuit = 0;
        }

        return true;
    }

}
