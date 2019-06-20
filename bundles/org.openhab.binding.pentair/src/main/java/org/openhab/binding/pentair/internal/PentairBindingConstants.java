/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pentair.internal;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link PentairBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairBindingConstants {

    public static final String BINDING_ID = "pentair";

    // List of Bridge Types
    public static final String IP_BRIDGE = "ip_bridge";
    public static final String SERIAL_BRIDGE = "serial_bridge";

    // List of all Device Types
    public static final String CONTROLLER = "controller";
    public static final String INTELLIFLO = "intelliflo";
    public static final String INTELLICHLOR = "intellichlor";

    // List of all Bridge Thing Type UIDs
    public static final ThingTypeUID IP_BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, IP_BRIDGE);
    public static final ThingTypeUID SERIAL_BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, SERIAL_BRIDGE);

    // List of all Thing Type UIDs
    public static final ThingTypeUID INTELLIFLO_THING_TYPE = new ThingTypeUID(BINDING_ID, INTELLIFLO);
    public static final ThingTypeUID CONTROLLER_THING_TYPE = new ThingTypeUID(BINDING_ID, CONTROLLER);
    public static final ThingTypeUID INTELLICHLOR_THING_TYPE = new ThingTypeUID(BINDING_ID, INTELLICHLOR);

    // Controller Groups and Items

    public static final String CONTROLLER_STATUS = "status";

    public static final String CONTROLLER_AIRTEMPERATURE = "airtemperature";
    public static final String CONTROLLER_SOLARTEMPERATURE = "solartemperature";
    public static final String CONTROLLER_LIGHTMODE = "lightmode";
    public static final String CONTROLLER_HEATACTIVE = "heatactive";
    public static final String CONTROLLER_UOM = "uom";
    public static final String CONTROLLER_SERVICEMODE = "servicemode";
    public static final String CONTROLLER_SOLARON = "solaron";
    public static final String CONTROLLER_HEATERON = "heateron";

    public static final String CONTROLLER_POOLCIRCUIT = "pool";
    public static final String CONTROLLER_SPACIRCUIT = "spa";
    public static final String CONTROLLER_AUX1CIRCUIT = "aux1";
    public static final String CONTROLLER_AUX2CIRCUIT = "aux2";
    public static final String CONTROLLER_AUX3CIRCUIT = "aux3";
    public static final String CONTROLLER_AUX4CIRCUIT = "aux4";
    public static final String CONTROLLER_AUX5CIRCUIT = "aux5";
    public static final String CONTROLLER_AUX6CIRCUIT = "aux6";
    public static final String CONTROLLER_AUX7CIRCUIT = "aux7";

    public static final String CONTROLLER_CIRCUITSWITCH = "switch";

    // List of heat group and items
    public static final String CONTROLLER_POOLHEAT = "poolheat";
    public static final String CONTROLLER_SPAHEAT = "spaheat";

    public static final String CONTROLLER_TEMPERATURE = "temperature";
    public static final String CONTROLLER_SETPOINT = "setpoint";
    public static final String CONTROLLER_HEATMODE = "heatmode";

    // List of schedule group and items
    public static final String CONTROLLER_SCHEDULE = "schedule%d";

    public static final String CONTROLLER_SCHEDULESTRING = "schedule";
    public static final String CONTROLLER_SCHEDULETYPE = "type";
    public static final String CONTROLLER_SCHEDULECIRCUIT = "circuit";
    public static final String CONTROLLER_SCHEDULEDAYS = "days";
    public static final String CONTROLLER_SCHEDULESTARTHOUR = "starthour";
    public static final String CONTROLLER_SCHEDULESTARTMIN = "startmin";
    public static final String CONTROLLER_SCHEDULEENDHOUR = "endhour";
    public static final String CONTROLLER_SCHEDULEENDMIN = "endmin";

    // List of Intellichlor channel ids
    public static final String INTELLICHLOR_SALTOUTPUT = "saltoutput";
    public static final String INTELLICHLOR_SALINITY = "salinity";

    // List of all Intelliflo channel ids
    public static final String INTELLIFLO_RUN = "run";
    public static final String INTELLIFLO_MODE = "mode";
    public static final String INTELLIFLO_DRIVESTATE = "drivestate";
    public static final String INTELLIFLO_POWER = "power";
    public static final String INTELLIFLO_RPM = "rpm";
    public static final String INTELLIFLO_PPC = "ppc";
    public static final String INTELLIFLO_ERROR = "error";
    public static final String INTELLIFLO_TIMER = "timer";
    public static final String INTELLIFLO_PROGRAM1 = "program1";
    public static final String INTELLIFLO_PROGRAM2 = "program2";
    public static final String INTELLIFLO_PROGRAM3 = "program3";
    public static final String INTELLIFLO_PROGRAM4 = "program4";

    public static final String DIAG = "diag";

    // Custom Properties
    public static final String PROPERTY_ADDRESS = "localhost";
    public static final Integer PROPERTY_PORT = 10000;

    // Set of all supported Thing Type UIDs
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(IP_BRIDGE_THING_TYPE, SERIAL_BRIDGE_THING_TYPE, CONTROLLER_THING_TYPE,
                    INTELLIFLO_THING_TYPE, INTELLICHLOR_THING_TYPE).collect(Collectors.toSet()));
}
