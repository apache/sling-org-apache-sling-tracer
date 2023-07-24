/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sling.tracer.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filter which returns false if the package of stack trace element
 * is part of exclude list of prefixes
 */
class PrefixExcludeFilter implements CallerFilter{
    private final List<String> prefixesToExclude;

    public PrefixExcludeFilter(List<String> prefixes) {
        this.prefixesToExclude = Collections.unmodifiableList(prefixes);
    }

    public static PrefixExcludeFilter from(String filter) {
        final List<String> prefixes = new ArrayList<>();
        for(String v : filter.split("\\|")) {
            v = v.trim();
            if (!v.isEmpty()) {
                prefixes.add(v);
            }
        }
        return new PrefixExcludeFilter(prefixes);
    }

    @Override
    public boolean include(StackTraceElement ste) {
        String className = ste.getClassName();
        for (String prefix : prefixesToExclude){
            if (className.startsWith(prefix)){
                return false;
            }
        }
        return true;
    }

    public List<String> getPrefixesToExclude() {
        return prefixesToExclude;
    }
}
