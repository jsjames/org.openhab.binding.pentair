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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

/**
 * PentairIntelliChemTest
 *
 * @author Jeff James - Initial contribution
 *
 */
public class PentairIntelliChemTest {

    public static byte[] ichempacket1 = javax.xml.bind.DatatypeConverter.parseHexBinary(
            "A50010901229030202A302D002C60000000000000000000000000006070000C8003F005A3C00580006A5201E01000000");
    public static byte[] ichempacket2 = javax.xml.bind.DatatypeConverter.parseHexBinary(
            "A5100F10122902E302AF02EE02BC000000020000002A0004005C060518019000000096140051000065203C0100000000");

    @Test
    public void test() {
        PentairIntelliChem pic = new PentairIntelliChem();
        PentairPacket p1 = new PentairPacket(ichempacket1, ichempacket1.length);

        pic.parsePacket(p1);

        assertThat(pic.phreading, equalTo(7.70));
        assertThat(pic.orpreading, equalTo(675));
        assertThat(pic.phsetpoint, equalTo(7.20));
        assertThat(pic.orpsetpoint, equalTo(710));
        assertThat(pic.tank1, equalTo(0));
        assertThat(pic.tank2, equalTo(6));
        // assertThat(pic.calciumhardness, equalTo(0));
        assertThat(pic.cyareading, equalTo(63));
        assertThat(pic.totalalkalinity, equalTo(0));
        assertThat(pic.waterflowalarm, equalTo(true));
        assertThat(pic.mode1, equalTo(0x06));
        assertThat(pic.mode2, equalTo(0xA5));

        assertThat(pic.calcCalciumHardnessFactor(), equalTo(1.0));

        PentairPacket p2 = new PentairPacket(ichempacket2, ichempacket2.length);
        pic.parsePacket(p2);

        assertThat(pic.phreading, equalTo(7.39));
        assertThat(pic.orpreading, equalTo(687));
        assertThat(pic.phsetpoint, equalTo(7.50));
        assertThat(pic.orpsetpoint, equalTo(700));
        assertThat(pic.tank1, equalTo(6));
        assertThat(pic.tank2, equalTo(5));
        // assertThat(pic.calciumhardness, equalTo(0));
        assertThat(pic.cyareading, equalTo(0));
        assertThat(pic.totalalkalinity, equalTo(150));
        assertThat(pic.waterflowalarm, equalTo(false));
        assertThat(pic.mode1, equalTo(0x65));
        assertThat(pic.mode2, equalTo(0x20));

        assertThat(pic.calcCalciumHardnessFactor(), equalTo(2.2));
    }

}
