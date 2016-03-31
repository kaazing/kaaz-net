/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.netx.http.internal;

import static java.util.Collections.unmodifiableMap;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class HttpHeaderFields  {

    private final List<String> keys;
    private final List<String> values;
    private final Map<String, List<String>> valuesByKey;
    private final Map<String, List<String>> valuesByKeyRO;

    public HttpHeaderFields() {
        keys = new LinkedList<String>();
        values = new LinkedList<String>();
        valuesByKey = new TreeMap<String, List<String>>(CASE_INSENSITIVE_ORDER_WITH_NULLS);
        valuesByKeyRO = unmodifiableMap(valuesByKey);
    }

    public void addAll(Map<String, List<String>> headerFields) {
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                add(key, value);
            }
        }
    }

    public void add(String key, String value) {
        keys.add(key);
        values.add(value);
        List<String> keyValues = valuesByKey.get(key);
        if (keyValues == null) {
            keyValues = new LinkedList<String>();
            valuesByKey.put(key, keyValues);
        }
        keyValues.add(value);
    }

    public void set(String key, String value) {
        for (Iterator<String> k$ = keys.iterator(), v$ = values.iterator(); k$.hasNext() && v$.hasNext();) {
            String k = k$.next();
            v$.next();
            if (k.equals(key)) {
                k$.remove();
                v$.remove();
            }
        }
        keys.add(key);
        values.add(value);
        List<String> keyValues = valuesByKey.get(key);
        if (keyValues == null) {
            keyValues = new LinkedList<String>();
        }
        else {
            valuesByKey.clear();
        }
        valuesByKey.put(key, keyValues);
        keyValues.add(value);
    }

    public String key(int index) {
        if (index < 0 || index >= keys.size()) {
            return null;
        }

        return keys.get(index);
    }

    public String value(int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }

        return values.get(index);
    }

    public String value(String key) {
        List<String> keyValues = valuesByKey.get(key);
        return (keyValues != null && !keyValues.isEmpty()) ? keyValues.get(0) : null;
    }

    public Map<String, List<String>> map() {
        return valuesByKeyRO;
    }

    public void clear() {
        keys.clear();
        values.clear();
        valuesByKey.clear();
    }

    private static final Comparator<String> CASE_INSENSITIVE_ORDER_WITH_NULLS = new CaseInsensitiveComparatorWithNulls();

    private static final class CaseInsensitiveComparatorWithNulls implements Comparator<String> {

        @Override
        public int compare(String o1, String o2) {
            if (o1 == null && o2 == null) {
                return 0;
            }

            if (o2 == null) {
                return -1 - o1.length();
            }

            if (o1 == null) {
                return o2.length() + 1;
            }

            return String.CASE_INSENSITIVE_ORDER.compare(o1, o2);
        }

    }
}
