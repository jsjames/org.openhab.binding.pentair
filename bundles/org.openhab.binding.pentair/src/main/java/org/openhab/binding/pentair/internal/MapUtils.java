/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.pentair.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link PentairBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jeff James - Initial contribution
 */

public final class MapUtils {
    public static <K, V> Map<K, V> mapOf(Object... keyValues) {
        Map<K, V> map = new HashMap<K, V>(keyValues.length / 2);

        for (int index = 0; index < keyValues.length / 2; index++) {
            map.put((K) keyValues[index * 2], (V) keyValues[index * 2 + 1]);
        }

        return map;
    }

    public static <A, B> Map<B, A> invertMap(Map<A, B> map) {
        Map<B, A> reverseMap = new HashMap<>();
        for (Map.Entry<A, B> entry : map.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        return reverseMap;
    }
}
