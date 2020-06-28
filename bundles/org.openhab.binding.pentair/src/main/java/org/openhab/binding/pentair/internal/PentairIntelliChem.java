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

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.openhab.binding.pentair.internal.handler.PentairControllerHandler;
import org.openhab.binding.pentair.internal.handler.PentairIntelliChlorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for the pentair controller schedules.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairIntelliChem {
    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChem.class);

    public static final int PHREADINGHI = 0;
    public static final int PHREADINGLO = 1;
    public static final int ORPREADINGHI = 2;
    public static final int ORPREADINGLO = 3;
    public static final int PHSETPOINTHI = 4;
    public static final int PHSETPOINTLO = 5;
    public static final int ORPSETPOINTHI = 6;
    public static final int ORPSETPOINTLO = 7;
    public static final int TANK1 = 20;
    public static final int TANK2 = 21;
    public static final int CALCIUMHARDNESSHI = 23;
    public static final int CALCIUMHARDNESSLO = 24;
    public static final int CYAREADING = 27;
    public static final int TOTALALKALINITYREADING = 28;
    public static final int WATERFLOW = 30;
    public static final int MODE1 = 34;
    public static final int MODE2 = 35;

    public double phreading;
    public int orpreading;
    public double phsetpoint;
    public int orpsetpoint; // Oxidation Reduction Potential
    public int tank1;
    public int tank2;
    public int calciumhardness;
    public int cyareading; // Cyanuric Acid
    public int totalalkalinity;
    public boolean waterflowalarm;
    public int mode1;
    public int mode2;
    public double saturationindex;

    public double calcCalciumHardnessFactor() {
        double CH = 0;

        if (calciumhardness <= 25) {
            CH = 1.0;
        } else if (calciumhardness <= 50) {
            CH = 1.3;
        } else if (calciumhardness <= 75) {
            CH = 1.5;
        } else if (calciumhardness <= 100) {
            CH = 1.6;
        } else if (calciumhardness <= 125) {
            CH = 1.7;
        } else if (calciumhardness <= 150) {
            CH = 1.8;
        } else if (calciumhardness <= 200) {
            CH = 1.9;
        } else if (calciumhardness <= 250) {
            CH = 2.0;
        } else if (calciumhardness <= 300) {
            CH = 2.1;
        } else if (calciumhardness <= 400) {
            CH = 2.2;
        } else if (calciumhardness <= 800) {
            CH = 2.5;
        }

        return CH;
    }

    public double calcTemperatureFactor(QuantityType<Temperature> t) {
        double TF = 0;
        int temp = t.intValue();

        if (t.getUnit() == SIUnits.CELSIUS) {
            if (temp <= 0) {
                TF = 0.0;
            } else if (temp <= 2.8) {
                TF = 0.1;
            } else if (temp <= 7.8) {
                TF = 0.2;
            } else if (temp <= 11.7) {
                TF = 0.3;
            } else if (temp <= 15.6) {
                TF = 0.4;
            } else if (temp <= 18.9) {
                TF = 0.5;
            } else if (temp <= 24.4) {
                TF = 0.6;
            } else if (temp <= 28.9) {
                TF = 0.7;
            } else if (temp <= 34.4) {
                TF = 0.8;
            } else if (temp <= 40.6) {
                TF = 0.9;
            }
        } else { // Fahrenheit
            if (temp <= 32) {
                TF = 0.0;
            } else if (temp <= 37) {
                TF = 0.1;
            } else if (temp <= 46) {
                TF = 0.2;
            } else if (temp <= 53) {
                TF = 0.3;
            } else if (temp <= 60) {
                TF = 0.4;
            } else if (temp <= 66) {
                TF = 0.5;
            } else if (temp <= 76) {
                TF = 0.6;
            } else if (temp <= 84) {
                TF = 0.7;
            } else if (temp <= 94) {
                TF = 0.8;
            } else if (temp <= 105) {
                TF = 0.9;
            }
        }

        return TF;
    }

    public double calcCorrectedAlkalinity() {
        return totalalkalinity - cyareading / 3;
    }

    public double calcAlkalinityFactor() {
        double ppm = calcCorrectedAlkalinity();
        double AF = 0;

        if (ppm <= 25) {
            AF = 1.4;
        } else if (ppm <= 50) {
            AF = 1.7;
        } else if (ppm <= 75) {
            AF = 1.9;
        } else if (ppm <= 100) {
            AF = 2.0;
        } else if (ppm <= 125) {
            AF = 2.1;
        } else if (ppm <= 150) {
            AF = 2.2;
        } else if (ppm <= 200) {
            AF = 2.3;
        } else if (ppm <= 250) {
            AF = 2.4;
        } else if (ppm <= 300) {
            AF = 2.5;
        } else if (ppm <= 400) {
            AF = 2.6;
        } else if (ppm <= 800) {
            AF = 2.9;
        }

        return AF;
    }

    public double calcTotalDisovledSolidsFactor() {
        // 12.1 for non-salt; 12.2 for salt

        if (PentairIntelliChlorHandler.onlineChlorinator != null) {
            return 12.2;
        }

        return 12.1;
    }

    public double calcSaturationIndex() {
        QuantityType<Temperature> temp;
        double AF;
        double TF;
        double SI;

        PentairControllerHandler pch = PentairControllerHandler.onlineController;

        if (pch != null) {
            temp = pch.getWaterTemp();
            TF = calcTemperatureFactor(temp);
        } else {
            TF = .4;
        }

        AF = calcAlkalinityFactor();

        SI = this.phreading + calcCalciumHardnessFactor() + AF + TF - calcTotalDisovledSolidsFactor();

        return SI;
        // Math.round((intellichem.readings.PH + calculateCalciumHardnessFactor() + calculateTotalCarbonateAlkalinity()
        // + calculateTemperatureFactor() - calculateTotalDisolvedSolidsFactor()) * 1000) / 1000
    }

    public void parsePacket(PentairPacket p) {
        if (p.getLength() != 41) {
            logger.debug("Intellichem packet not 41 bytes long");
            return;
        }

        phreading = (((p.getByte(PHREADINGHI) & 0xFF) * 256) + (p.getByte(PHREADINGLO) & 0xFF)) / 100.0;
        orpreading = ((p.getByte(ORPREADINGHI) & 0xFF) * 256) + (p.getByte(ORPREADINGLO) & 0xFF);
        phsetpoint = (((p.getByte(PHSETPOINTHI) & 0xFF) * 256) + (p.getByte(PHSETPOINTLO) & 0xFF)) / 100.0;
        orpsetpoint = ((p.getByte(ORPSETPOINTHI) & 0xFF) * 256) + (p.getByte(ORPSETPOINTLO) & 0xFF);
        tank1 = p.getByte(TANK1);
        tank2 = p.getByte(TANK2);
        calciumhardness = ((p.getByte(CALCIUMHARDNESSHI) & 0xFF) * 256) + (p.getByte(CALCIUMHARDNESSLO) & 0xFF);
        cyareading = p.getByte(CYAREADING);
        totalalkalinity = (p.getByte(TOTALALKALINITYREADING) & 0xFF);
        waterflowalarm = p.getByte(WATERFLOW) != 0x00;
        mode1 = p.getByte(MODE1);
        mode2 = p.getByte(MODE2);

        saturationindex = calcSaturationIndex();
    }

    @Override
    public String toString() {
        String str = String.format(
                "PH: %.2f, OPR: %d, PH set point: %.2f, ORP set point: %d, tank1: %d, tank2: %d, calcium hardness: %d, cyareading: %d, total alkalinity: %d, water flow alarm: %b, mode1: %h, mode2: %h, saturationindex: %f.1",
                phreading, orpreading, phsetpoint, orpsetpoint, tank1, tank2, calciumhardness, cyareading,
                totalalkalinity, waterflowalarm, mode1, mode2, saturationindex);

        return str;
    }
}
