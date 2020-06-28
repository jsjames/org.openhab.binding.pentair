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
 * Generic class for the standard pentair package protocol. Includes helpers to generate checksum and extract key bytes
 * from packet.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairPacket implements Cloneable {
    protected static final char[] HEXARRAY = "0123456789ABCDEF".toCharArray();

    public static final int OFFSET = 2;
    public static final int DEST = 0 + OFFSET;
    public static final int SOURCE = 1 + OFFSET;
    public static final int ACTION = 2 + OFFSET;
    public static final int LENGTH = 3 + OFFSET;
    public static final int STARTOFDATA = 4 + OFFSET;

    protected boolean initialized;

    public byte[] buf;

    /**
     * Constructor for PentairPacket basic packet.
     */
    public PentairPacket() {
        buf = new byte[6];

        buf[0] = (byte) 0xA5;
    }

    /**
     * Constructor for a PentairPackage with a byte array for the command. Typically used when generating a packet to
     * send. Should include all bytes starting with A5. Do not include checksum bytes.
     *
     * @param buf Array of bytes to be used to populate packet.
     */
    public PentairPacket(byte[] buf) {
        this(buf, buf.length);
    }

    public PentairPacket(byte[] buf, int l) {
        this.buf = new byte[l];
        System.arraycopy(buf, 0, this.buf, 0, l);

        initialized = true;
    }

    /**
     * Constructor to create a copy of PentairPacket p. Note references the same byte array as original. Used when
     * coverting from a generic packet to a specialized packet.
     *
     * @param p PentairPacket to duplicate in new copy.
     */
    public PentairPacket(PentairPacket p) {
        this.buf = p.buf;

        initialized = true;
    }

    public int getBufLength() {
        return buf.length;
    }

    /**
     * Gets length of packet
     *
     * @return length of packet
     */
    public int getLength() {
        return (buf[LENGTH] & 0xFF);
    }

    /**
     * Sets length of packet
     *
     * @param length length of packet
     */
    public void setLength(int length) {
        if (length > (buf[LENGTH] & 0xFF)) {
            buf = new byte[length + 6];
        }
        buf[LENGTH] = (byte) length;
    }

    /**
     * Gets action byte of packet
     *
     * @return action byte of packet
     */
    public int getAction() {
        return (buf[ACTION] & 0xFF); // need to convert to unsigned value
    }

    /**
     * Sets action byte of packet
     *
     * @param action
     */
    public void setAction(int action) {
        buf[ACTION] = (byte) action;
    }

    /**
     * Gets source byte or packet
     *
     * @return source byte of packet
     */
    public int getSource() {
        return (buf[SOURCE] & 0xFF);
    }

    /**
     * Sets source byte of packet
     *
     * @param source sets source byte of packet
     */
    public void setSource(int source) {
        buf[SOURCE] = (byte) source;
    }

    /**
     * Gets destination byte of packet
     *
     * @return destination byte of packet
     */
    public int getDest() {
        return (buf[DEST] & 0xFF);
    }

    /**
     * Sets destination byte of packet
     *
     * @param dest destination byte of packet
     */
    public void setDest(int dest) {
        buf[DEST] = (byte) dest;
    }

    /**
     * Gets the preamble byte of packet
     */
    public int getPreambleByte() {
        return (buf[1] & 0xFF);
    }

    /**
     * Gets a particular byte of packet
     *
     * @param number of byte in packet
     */
    public int getByte(int num) {
        int num2;

        num2 = STARTOFDATA + num;
        if (num2 > buf.length) {
            return -1;
        }
        return (buf[num2] & 0xFF);
    }

    public void setByte(int num, byte b) {
        int num2;

        num2 = STARTOFDATA + num;
        if (num2 > buf.length) {
            return;
        }

        buf[num2] = b;
    }

    /**
     * Helper function to convert byte to hex representation
     *
     * @param b byte to re
     * @return 2 charater hex string representing the byte
     */
    public static String byteToHex(int b) {
        char[] hexChars = new char[2];

        hexChars[0] = HEXARRAY[b >>> 4];
        hexChars[1] = HEXARRAY[b & 0x0F];

        return new String(hexChars);
    }

    /**
     * @param bytes array of bytes to convert to a hex string. Entire buf length is converted.
     * @return hex string
     */
    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, bytes.length);
    }

    /**
     * @param bytes array of bytes to convert to a hex string.
     * @param len Number of bytes to convert
     * @return hex string
     */
    public static String bytesToHex(byte[] bytes, int len) {
        char[] hexChars = new char[len * 3];
        for (int j = 0; j < len; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEXARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEXARRAY[v & 0x0F];
            hexChars[j * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return bytesToHex(buf, getLength() + 6);
    }

    /**
     * Calculate checksum of the representative packet.
     *
     * @return checksum of packet
     */
    public int calcChecksum() {
        int checksum = 0, i;

        for (i = 0; i < getLength() + 6; i++) {
            checksum += buf[i] & 0xFF;
        }

        return checksum;
    }

    public byte[] getFullWriteStream() {
        int checksum;

        byte[] preamble = { (byte) 0xFF, (byte) 0x00, (byte) 0xFF };
        byte[] writebuf;

        writebuf = new byte[preamble.length + buf.length + 2];

        System.arraycopy(preamble, 0, writebuf, 0, preamble.length);
        System.arraycopy(this.buf, 0, writebuf, preamble.length, buf.length);

        checksum = calcChecksum();

        writebuf[writebuf.length - 2] = (byte) ((checksum >> 8) & 0xFF);
        writebuf[writebuf.length - 1] = (byte) (checksum & 0xFF);

        return writebuf;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        PentairPacket p = (PentairPacket) super.clone();

        p.buf = new byte[this.buf.length];
        System.arraycopy(this.buf, 0, p.buf, 0, this.buf.length);

        return p;
    }
}
