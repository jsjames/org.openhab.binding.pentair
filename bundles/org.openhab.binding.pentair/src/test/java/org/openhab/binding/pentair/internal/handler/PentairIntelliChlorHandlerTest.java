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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.pentair.internal.PentairPacket;

/**
 * PentairIntelliChloreHandlerTest
 *
 * @author Jeff James - Initial contribution
 *
 */
public class PentairIntelliChlorHandlerTest {
    public static byte[] parsehex(String in) {
        String out = in.replaceAll("\\s", "");

        return javax.xml.bind.DatatypeConverter.parseHexBinary(out);
    }

    //@formatter:off
    public static byte[] packet1 = parsehex("10 02 50 11 50 C3");
    public static byte[] packet2 = parsehex("10 02 00 12 67 80 0B");
    //@formatter:on

    private PentairIntelliChlorHandler pic_handler;

    @Mock
    private Bridge bridge;

    @Mock
    private ThingHandlerCallback callback;

    @Mock
    private Thing thing;

    @Mock
    private PentairIPBridgeHandler pibh;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        // when(thing.getConfiguration()).thenReturn(new Configuration(Collections.singletonMap("id", 144)));
        when(thing.getUID()).thenReturn(new ThingUID("1:2:3"));

        pibh = new PentairIPBridgeHandler(bridge);

        pic_handler = new PentairIntelliChlorHandler(thing) {
            @Override
            public PentairBaseBridgeHandler getBridgeHandler() {
                return pibh;
            }
        };

        pic_handler.setCallback(callback);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {
        pic_handler.initialize();

        PentairPacket p = new PentairPacket(packet1, packet1.length);

        pic_handler.processPacketFrom(p);

        verify(callback, times(1)).statusUpdated(eq(thing), argThat(arg -> arg.getStatus().equals(ThingStatus.ONLINE)));

        ChannelUID cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLICHLOR_SALTOUTPUT);
        verify(callback, times(1)).stateUpdated(cuid, new DecimalType(80));

        PentairPacket p2 = new PentairPacket(packet2, packet2.length);

        pic_handler.processPacketFrom(p2);

        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLICHLOR_SALINITY);
        verify(callback, times(1)).stateUpdated(cuid, new DecimalType(5150));

        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLICHLOR_OK);

        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
    }

}
