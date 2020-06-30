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
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.pentair.internal.PentairParser.CallbackPentairParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PentairParserTest
 *
 * @author Jeff James - Initial contribution
 *
 */
public class PentairParserTest {
    private final Logger logger = LoggerFactory.getLogger(PentairParserTest.class);

    public static byte[] parsehex(String in) {
        String out = in.replaceAll("\\s", "");

        return javax.xml.bind.DatatypeConverter.parseHexBinary(out);
    }

    //@formatter:off
    public static byte[] stream = parsehex(
            "FF 00 FF A5 1E 0F 10 02 1D 09 1F 00 00 00 00 00 00 00 20 03 00 00 04 3F 3F 00 00 41 3C 00 00 07 00 00 6A B6 00 0D 03 7F"
                    + "FF 00 FF A5 10 0F 10 12 29 02 E3 02 AF 02 EE 02 BC 00 00 00 02 00 00 00 2A 00 04 00 5C 06 05 18 01 90 00 00 00 96 14 00 51 00 00 65 20 3C 01 00 00 00 07 50 "
                    + "FF 00 FF A5 01 0F 10 02 1D 0D 1D 20 00 00 00 00 00 00 00 33 00 00 04 4D 4D 00 00 51 6D 00 00 07 00 00 5E D5 00 0D 04 04");

    public static byte[] stream2 = parsehex(
            "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 00 00 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 88 BE 00 0D 03 C7"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 00 00 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 88 BE 00 0D 03 C7"
                    + "FF 00 FF A5 10 10 22 86 02 0B 01 01 7B"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 86 01 6F"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 04 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 88 BE 00 0D 03 EB"
                    + "FF 00 FF A5 00 60 10 07 00 01 1C"
                    + "FF 00 FF A5 00 10 60 07 0F 04 00 00 00 00 00 00 00 00 00 00 00 00 14 1E 01 61 FF 00 FF A5 00 61"
                    + "10 07 00 01 1D"
                    + "FF 00 FF A5 00 10 61 07 0F 04 00 00 00 00 00 00 00 00 00 00 00 00 14 1E 01 62"
                    + "FF 00 FF A5 10 10 22 86 02 06 00 01 75"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 86 01 6F"
                    + "10 02 50 11 00 73 10 03"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 04 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 88 BE 00 0D 03 EB"
                    + "FF 00 FF A5 00 60 10 04 01 FF 02 19"
                    + "FF 00 FF A5 00 10 60 04 01 FF 02 19"
                    + "FF 00 FF A5 00 60 10 06 01 0A 01 26"
                    + "FF 00 FF A5 00 10 60 06 01 0A 01 26"
                    + "FF 00 FF A5 00 60 10 01 04 02 C4 07 6C 02 53"
                    + "FF 00 FF A5 00 10 60 01 02 07 6C 01 8B"
                    + "FF 00 FF A5 00 61 10 04 01 FF 02 1A FF 00 FF A5 00 10 61 04 01 FF 02 1A"
                    + "FF 00 FF A5 00 61 10 06 01 04 01 21"
                    + "FF 00 FF A5 00 10 61 06 01 04 01 21"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 04 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 88 BE 00 0D 03 EB"
                    + "FF 00 FF A5 10 10 22 88 04 52 64 07 00 02 30"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 88 01 71"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 04 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 07 00 00 7D C1 00 0D 03 E6 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 C5 01 00"
                    + "01 AB FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 05 08 14 1E 04 07 02 11 00 01 01 32 FF FF FF FF"
                    + "FF FF FF FF 00 FF A5 10 10 20 C8 01 00 01 AE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 08 0D 39 39 3A 52 64 07 00 00 38 00 00 00 00 02 8A"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 00 01 B0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 00 57 74 72 46 61 6C 6C 20 31 00 FB 04 F2 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 01 01 B1"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 01 57 74 72 46 61 6C 6C 20 31 2E 35 04 5B FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 02 01 B2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 02 57 74 72 46 61 6C 6C 20 32 00 FB 04 F5 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 03 01 B3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 03 57 74 72 46 61 6C 6C 20 33 00 FB 04 F7 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 04 01 B4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 04 50 6F 6F 6C 20 4C 6F 77 32 00 FB 05 07 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 05 01 B5"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 05 55 53 45 52 4E 41 4D 45 2D 30 36 03 E2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 06 01 B6"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 06 55 53 45 52 4E 41 4D 45 2D 30 37 03 E4 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 07 01 B7"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 07 55 53 45 52 4E 41 4D 45 2D 30 38 03 E6 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 08 01 B8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 08 55 53 45 52 4E 41 4D 45 2D 30 39 03 E8 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 09 01 B9"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 09 55 53 45 52 4E 41 4D 45 2D 31 30 03 E1 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 01 01 B2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 01 01 48 00 00 01 2E FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 02 01 B3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 02 00 2E 00 00 01 14"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 03 01 B4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 03 00 02 00 00 00 E9 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 04 01 B5 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 04 05 16 00 00 01"
                    + "03 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 05 01 B6 FF FF FF FF FF FF FF FF 00 FF A5 10"
                    + "0F 10 0B 05 05 40 C9 00 00 01 F2 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 06 01 B7 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 06 42 3D 00 00 01 69 FF FF FF FF FF FF FF FF 00 FF A5"
                    + "10 10 20 CB 01 07 01 B8 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 07 07 4A 00 00 01 3C FF"
                    + "FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 08 01 B9 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10"
                    + "0B 05 08 07 3F 00 00 01 32 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 09 01 BA FF FF FF FF"
                    + "FF FF FF FF 00 FF A5 10 0F 10 0B 05 09 07 37 00 00 01 2B FF FF FF FF FF FF FF FF 00 FF A5 10 10"
                    + "20 CB 01 0A 01 BB FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0A 00 00 00 00 00 EE FF FF FF"
                    + "FF FF FF FF FF 00 FF A5 10 10 20 CB 01 0B 01 BC FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05"
                    + "0B 0E 4F 00 00 01 4C FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 0C 01 BD FF FF FF FF FF FF"
                    + "FF FF 00 FF A5 10 0F 10 0B 05 0C 00 C8 00 00 01 B8 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB"
                    + "01 0D 01 BE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0D 00 CA 00 00 01 BB FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0E 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0E 00 CB 00 00 01 BD FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0F 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0F 00 CC 00 00 01 BF FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 10 01 C1"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 10 0E 35 00 00 01 37"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 11 01 C2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 11 0E 35 00 00 01 38 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 12 01 C3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 12 0E 35 00 00 01 39 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 D1 01 01 01 B8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 01 06 09 19 0F 37 FF 02 5A"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 02 01 B9 FF FF FF FF FF FF FF FF 00 FF A5 10 0F"
                    + "10 11 07 02 0D 0E 39 0F 08 D5 02 2E"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 03 01 BA"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 03 04 0A 0F 0B 00 FF 02 16 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 04 01 BB"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 04 06 19 00 07 0F 00 01 25 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 05 01 BC"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 05 04 19 00 04 00 00 01 12 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 06 01 BD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 06 0F 15 0A 17 37 FF 02 6D"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 07 01 BE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 07 0F 00 05 09 14 FF 02 23 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 08 01 BF FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 08 07 19 00"
                    + "02 00 00 01 16 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 09 01 C0 FF FF FF FF FF FF FF FF"
                    + "00 FF A5 10 0F 10 11 07 09 02 19 00 03 2D 00 01 40 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1"
                    + "01 0A 01 C1 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 0A 09 19 00 04 0F 00 01 2B FF FF FF"
                    + "FF FF FF FF FF 00 FF A5 10 10 20 D1 01 0B 01 C2 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07"
                    + "0B 0B 0D 00 0D 0B FF 02 26 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 0C 01 C3 FF FF FF FF"
                    + "FF FF FF FF 00 FF A5 10 0F 10 11 07 0C 05 0D 14 0D 28 95 01 E8 FF FF FF FF FF FF FF FF 00 FF A5"
                    + "10 10 20 E2 01 00 01 C8 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 22 03 07 80 44 01 C4 FF FF FF"
                    + "FF FF FF FF FF 00 FF A5 10 10 20 E3 01 00 01 C9 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 23 02"
                    + "10 00 01 09"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E8 01 00 01 CE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 28 0A 00 00 00 FE 01 00 00 00 00 00 02 05 FF FF FF FF"
                    + "FF FF FF FF 00 FF A5 10 10 20 DE 01 00 01 C4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 1E 10 00 00 00 00 01 48 00 00 00 2E 00 00 00 02 00 00"
                    + "01 7B FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E1 01 00 01 C7 FF FF FF FF FF FF FF FF 00 FF A5"
                    + "10 0F 10 21 04 01 02 03 04 01 03 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E0 01 00 01 C6"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 20 0B 00 07 02 01 08 05 06 07 08 09 0A 01 3E FF FF FF"
                    + "FF FF FF FF FF 00 FF A5 10 10 20 DD 01 00 01 C3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 1D 18 02 00 00 00 80 01 FF FF FF 00 07 02 01 08 05 06"
                    + "07 08 09 0A 01 02 03 04 04 D2 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D9 01 00 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 19 16 0B 08 80 1C 85 00 49 6E 74 65 6C 6C 69 63 68 6C"
                    + "6F 72 2D 2D 34 30 07 DE FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D6 01 00 01 BC FF FF FF FF FF"
                    + "FF FF FF 00 FF A5 10 0F 10 16 10 00 02 07 6C 00 01 32 0A 01 90 0D 7A 0F 82 00 00 03 55"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E7 01 00 01 CD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 27 20 08 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00"
                    + "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 2C FF FF FF FF FF FF FF FF 00 FF A5 10 10 20"
                    + "E0 01 01 01 C7 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 20 0B 01 01 02 03 04 05 06 07 08 09 0A"
                    + "01 37 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D8 01 01 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 18 1F 01 80 00 02 00 01 06 02 0C 04 09 0B 07 06 05 80"
                    + "08 84 03 0F 03 03 D6 80 2E 6C 14 AC E8 20 E8 07 91"
                    + "FF 00 FF A5 10 10 22 88 04 53 64 07 00 02 31"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 88 01 71"
                    + "FF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D8 01 02 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 18 1F 02 80 03 02 00 0C 03 05 05 0D 07 0E 0B 00 03 00"
                    + "03 00 03 00 03 0C E8 DC D0 B8 E8 E8 E8 E8 1C 08 F8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 04 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 07 00 00 81 C2 00 0D 03 EB"
                    + "FF 00 FF A5 10 10 22 88 04 53 64 04 00 02 2E"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 88 01 71"
                    + "FF 00 FF A5 00 60 10 04 01 FF 02 19"
                    + "FF 00 FF A5 00 10 60 04 01 FF 02 19"
                    + "FF 00 FF A5 00 60 10 06 01 0A 01 26"
                    + "FF 00 FF A5 00 10 60 06 01 0A 01 26 FF 00 FF A5 00 60 10 01 04 02 C4 07 6C 02 53 FF 00 FF A5 00"
                    + "10 60 01 02 07 6C 01 8B FF 00 FF A5 00 61 10 04 01 FF 02 1A FF 00 FF A5 00 10 61 04 01 FF 02 1A"
                    + "FF 00 FF A5 00 61 10 06 01 04 01 21 FF 00 FF A5 00 10 61 06 01 04 01 21"
                    + "FF 00 FF A5 10 10 22 86 02 0B 00 01 7A"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 86 01 6F"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 00 00 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 82 BF 00 0D 03 C2 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 C5 01 00"
                    + "01 AB"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 05 08 14 1E 04 07 02 11 00 01 01 32 FF FF FF FF FF FF"
                    + "FF FF 00 FF A5 10 10 20 C8 01 00 01 AE FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 08 0D 39 39 3A"
                    + "53 64 04 00 00 38 00 00 00 00 02 88"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 00 01 B0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 00 57 74 72 46 61 6C 6C 20 31 00 FB 04 F2 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 01 01 B1"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 01 57 74 72 46 61 6C 6C 20 31 2E 35 04 5B FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 02 01 B2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 02 57 74 72 46 61 6C 6C 20 32 00 FB 04 F5 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 03 01 B3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 03 57 74 72 46 61 6C 6C 20 33 00 FB 04 F7 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 04 01 B4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 04 50 6F 6F 6C 20 4C 6F 77 32 00 FB 05 07"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 05 01 B5"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 05 55 53 45 52 4E 41 4D 45 2D 30 36 03 E2 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 06 01 B6"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 06 55 53 45 52 4E 41 4D 45 2D 30 37 03 E4 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 07 01 B7"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 07 55 53 45 52 4E 41 4D 45 2D 30 38 03 E6 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 08 01 B8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A 0C 08 55 53 45 52 4E 41 4D 45 2D 30 39 03 E8 FF FF"
                    + "FF FF FF FF FF FF 00 FF A5 10 10 20 CA 01 09 01 B9 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0A"
                    + "0C 09 55 53 45 52 4E 41 4D 45 2D 31 30 03 E1 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 01"
                    + "01 B2 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 01 01 48 00 00 01 2E FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 CB 01 02 01 B3 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 02 00 2E 00"
                    + "00 01 14 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 03 01 B4 FF FF FF FF FF FF FF FF 00 FF"
                    + "A5 10 0F 10 0B 05 03 00 02 00 00 00 E9 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 04 01 B5"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 04 05 16 00 00 01 03 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 05 01 B6 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 05 40 C9 00 00 01"
                    + "F2 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 06 01 B7 FF FF FF FF FF FF FF FF 00 FF A5 10"
                    + "0F 10 0B 05 06 42 3D 00 00 01 69 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 07 01 B8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 07 07 4A 00 00 01 3C FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 08 01 B9"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 08 07 3F 00 00 01 32 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 09 01 BA"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 09 07 37 00 00 01 2B FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0A 01 BB"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0A 00 00 00 00 00 EE FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0B 01 BC"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0B 0E 4F 00 00 01 4C FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0C 01 BD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0C 00 C8 00 00 01 B8 FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0D 01 BE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0D 00 CA 00 00 01 BB FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 0E 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0E 00 CB 00 00 01 BD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 0F 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 0F 00 CC 00 00 01 BF FF FF FF FF FF FF FF FF 00"
                    + "FF A5 10 10 20 CB 01 10 01 C1"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 10 0E 35 00 00 01 37"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 11 01 C2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 0B 05 11 0E 35 00 00 01 38"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 CB 01 12 01 C3 FF FF FF FF FF FF FF FF 00 FF A5 10 0F"
                    + "10 0B 05 12 0E 35 00 00 01 39"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 01 01 B8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 01 06 09 19 0F 37 FF 02 5A FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 02 01 B9"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 02 0D 0E 39 0F 08 D5 02 2E FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 03 01 BA"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 03 04 0A 0F 0B 00 FF 02 16"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 04 01 BB"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 04 06 19 00 07 0F 00 01 25 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 05 01 BC"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 05 04 19 00 04 00 00 01 12 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 06 01 BD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 06 0F 15 0A 17 37 FF 02 6D FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 07 01 BE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 07 0F 00 05 09 14 FF 02 23 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 08 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 08 07 19 00 02 00 00 01 16 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 09 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 09 02 19 00 03 2D 00 01 40"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 0A 01 C1"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 0A 09 19 00 04 0F 00 01 2B"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D1 01 0B 01 C2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 0B 0B 0D 00 0D 0B FF 02 26 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 D1 01 0C 01 C3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 11 07 0C 05 0D 14 0D 28 95 01 E8 FF FF FF FF FF FF FF"
                    + "FF 00 FF A5 10 10 20 E2 01 00 01 C8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 22 03 07 80 44 01 C4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E3 01 00 01 C9"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 23 02 10 00 01 09"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E8 01 00 01 CE"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 28 0A 00 00 00 FE 01 00 00 00 00 00 02 05 FF FF FF FF"
                    + "FF FF FF FF 00 FF A5 10 10 20 DE 01 00 01 C4"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 1E 10 00 00 00 00 01 48 00 00 00 2E 00 00 00 02 00 00"
                    + "01 7B FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E1 01 00 01 C7 FF FF FF FF FF FF FF FF 00 FF A5"
                    + "10 0F 10 21 04 01 02 03 04 01 03 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E0 01 00 01 C6"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 20 0B 00 07 02 01 08 05 06 07 08 09 0A 01 3E"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 DD 01 00 01 C3"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 1D 18 02 00 00 00 80 01 FF FF FF 00 07 02 01 08 05 06"
                    + "07 08 09 0A 01 02 03 04 04 D2 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D9 01 00 01 BF FF FF FF"
                    + "FF FF FF FF FF 00 FF A5 10 0F 10 19 16 0B 08 80 1C 85 00 49 6E 74 65 6C 6C 69 63 68 6C 6F 72 2D"
                    + "2D 34 30 07 DE FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D6 01 00 01 BC FF FF FF FF FF FF FF FF"
                    + "00 FF A5 10 0F 10 16 10 00 02 00 00 00 01 32 0A 01 90 0D 7A 0F 82 00 00 02 E2"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 E7 01 00 01 CD"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 27 20 08 00 00 00 09 00 00 00 00 00 00 00 00 00 00 00"
                    + "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 2C FF FF FF FF FF FF FF FF 00 FF A5 10 10 20"
                    + "E0 01 01 01 C7 FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 20 0B 01 01 02 03 04 05 06 07 08 09 0A"
                    + "01 37 FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D8 01 01 01 BF"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 18 1F 01 80 00 02 00 01 06 02 0C 04 09 0B 07 06 05 80"
                    + "08 84 03 0F 03 03 D6 80 2E 6C 14 AC E8 20 E8 07 91"
                    + "FF 00 FF A5 10 10 22 86 02 06 01 01 76"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 22 10 01 01 86 01 6F"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 00 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 82 BF 00 0D 03 E2"
                    + "FF 10 02 50 11 50 C3 10 03"
                    + "10 02 00 12 67 80 0B 10 03"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 10 20 D8 01 02 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 18 1F 02 80 03 02 00 0C 03 05 05 0D 07 0E 0B 00 03 00"
                    + "03 00 03 00 03 0C E8 DC D0 B8 E8 E8 E8 E8 1C 08 F8"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 02 1D 14 1E 20 00 00 00 00 00 00 00 03 00 40 04 39 39"
                    + "20 00 3A 38 00 00 04 00 00 82 BF 00 0D 03 E2"
                    + "FF 00 FF A5 00 60 10 04 01 FF 02 19"
                    + "FF 00 FF A5 00 10 60 04 01 FF 02 19"
                    + "FF 00 FF A5 00 60 10 06 01 0A 01 26"
                    + "FF 00 FF A5 00 10 60 06 01 0A 01 26"
                    + "FF 00 FF A5 00 60 10 01 04 02 C4 05 14 01 F9"
                    + "FF 00 FF A5 00 10 60 01 02 05 14 01 31"
                    + "FF 00 FF A5 00 61 10 04 01 FF 02 1A"
                    + "FF 00 FF A5 00 10 61 04 01 FF 02 1A"
                    + "FF 00 FF A5 00 61 10 06 01 04 01 21"
                    + "FF 00 FF A5 00 10 61 06 01 04 01 21"
                    + "FF 00 FF A5 10 10 22 D7 01 01 01 C0"
                    + "FF FF FF FF FF FF FF FF 00 FF A5 10 0F 10 17 10 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 80"
                    + "01 7C"
                    + "FF 00 FF A5 10 10 22 D7 01 02 01 C1"
            );
    //@formatter:on

    PentairParser parser = new PentairParser();

    @Mock
    CallbackPentairParser callback;

    @Captor
    ArgumentCaptor<PentairPacket> packets;

    @Captor
    ArgumentCaptor<PentairPacket> packetsIntellichlor;

    Thread thread;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        parser.setCallback(callback);
    }

    @After
    public void tearDown() throws Exception {
        if (thread != null) {
            thread.interrupt();
            thread.join();
        }
        thread = null;
    }

    @Test
    public void test() throws InterruptedException {
        java.lang.System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");

        logger.debug("debug");
        logger.info("info");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream, 0, stream.length);

        parser.setInputStream(inputStream);

        thread = new Thread(parser);
        thread.start();

        Thread.sleep(2000);

        thread.interrupt();

        thread.join();
        thread = null;

        verify(callback, times(3)).onPentairPacket(packets.capture());

        List<PentairPacket> allPackets = new ArrayList<PentairPacket>();
        allPackets = packets.getAllValues();

        assertThat(allPackets.size(), equalTo(3));

        logger.info("1: {}", allPackets.get(0).getAction());
        logger.info("2: {}", allPackets.get(1).getAction());
        logger.info("3: {}", allPackets.get(2).getAction());

        assertThat(allPackets.get(0).getAction(), equalTo(0x02));

        assertThat(allPackets.get(1).getAction(), equalTo(0x12));

        assertThat(allPackets.get(2).getAction(), equalTo(0x02));
    }

    @Test
    public void test2() throws InterruptedException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream2, 0, stream2.length);

        logger.debug("THIS IS A DEBUG");

        parser.setInputStream(inputStream);

        thread = new Thread(parser);
        thread.start();

        Thread.sleep(2000);

        thread.interrupt();

        thread.join();
        thread = null;

        verify(callback, atLeast(1)).onPentairPacket(packets.capture());
        verify(callback, atLeast(1)).onIntelliChlorPacket(packetsIntellichlor.capture());

        List<PentairPacket> allPackets = new ArrayList<PentairPacket>();
        allPackets = packets.getAllValues();

        logger.info("Number of Pentair packets: {}", allPackets.size());

        assertThat(allPackets.size(), equalTo(281));

        List<PentairPacket> allPacketsIntellichlor = new ArrayList<PentairPacket>();
        allPacketsIntellichlor = packetsIntellichlor.getAllValues();

        logger.info("Number of Intellichlor packets: {}", allPacketsIntellichlor.size());

        assertThat(allPacketsIntellichlor.size(), equalTo(3));

    }

}
