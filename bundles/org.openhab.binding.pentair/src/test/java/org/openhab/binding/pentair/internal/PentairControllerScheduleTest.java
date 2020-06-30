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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * PentairControllerSchduleTest
 *
 * @author Jeff James - Initial contribution
 *
 */
public class PentairControllerScheduleTest {
    public static byte[] parsehex(String in) {
        String out = in.replaceAll("\\s", "");

        return javax.xml.bind.DatatypeConverter.parseHexBinary(out);
    }

    //@formatter:off
    public static byte[] packet1 = parsehex("A5 1E 0F 10 11 07 01 06 0A 00 10 00 7F");
    public static byte[] packet2 = parsehex("A5 1E 0F 10 11 07 02 05 0A 00 0B 00 7F");
    public static byte[] packet3 = parsehex("A5 1E 0F 10 11 07 03 07 08 00 1A 00 08");
    public static byte[] packet4 = parsehex("A5 1E 0F 10 11 07 04 09 19 00 02 15 0F");

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void ParseTest() {
        PentairControllerSchedule pcs = new PentairControllerSchedule();

        PentairPacket p = new PentairPacket(packet1);

        pcs.parsePacket(p);
        assertThat(pcs.circuit, equalTo(6));
        assertThat(pcs.start, equalTo(10 * 60));
        assertThat(pcs.end, equalTo(16 * 60));
        assertThat(pcs.days, equalTo(0x7F));
        assertThat(pcs.type, equalTo(PentairControllerSchedule.SCHEDULETYPE_NORMAL));
        assertThat(pcs.id, equalTo(1));

        PentairPacket p2 = new PentairPacket(packet2);
        pcs.parsePacket(p2);
        assertThat(pcs.circuit, equalTo(5));
        assertThat(pcs.start, equalTo(10 * 60));
        assertThat(pcs.end, equalTo(11 * 60));
        assertThat(pcs.days, equalTo(0x7F));
        assertThat(pcs.type, equalTo(PentairControllerSchedule.SCHEDULETYPE_NORMAL));
        assertThat(pcs.id, equalTo(2));

        PentairPacket p3 = new PentairPacket(packet3);
        pcs.parsePacket(p3);
        assertThat(pcs.circuit, equalTo(7));
        assertThat(pcs.start, equalTo(8 * 60));
        assertThat(pcs.days, equalTo(0x08));
        assertThat(pcs.type, equalTo(PentairControllerSchedule.SCHEDULETYPE_ONCEONLY));
        assertThat(pcs.id, equalTo(3));

        PentairPacket p4 = new PentairPacket(packet4);
        pcs.parsePacket(p4);
        assertThat(pcs.circuit, equalTo(9));
        assertThat(pcs.end, equalTo(0x02 * 60 + 0x15));
        assertThat(pcs.days, equalTo(0x0F));
        assertThat(pcs.type, equalTo(PentairControllerSchedule.SCHEDULETYPE_EGGTIMER));
        assertThat(pcs.id, equalTo(4));
    }

    @Test
    public void SetTest() {
        PentairControllerSchedule pcs = new PentairControllerSchedule();

        pcs.id = 1;
        pcs.circuit = 4;
        pcs.start = 5 * 60 + 15;    // 5:15
        pcs.end = 10 * 60 + 30;     // 10:30
        pcs.type = PentairControllerSchedule.SCHEDULETYPE_NORMAL;
        pcs.days = 0x07;

        PentairPacket p = pcs.getWritePacket(0x10, 0x00);

        assertThat(p.buf, is(parsehex("A5 00 10 00 91 07 01 04 05 0F 0A 1E 07")));
    }
}
