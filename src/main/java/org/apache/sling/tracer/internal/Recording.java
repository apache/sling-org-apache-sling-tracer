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

import org.apache.sling.api.request.RequestProgressTracker;

interface Recording {
    Recording NOOP = new Recording() {
        @Override
        public void log(String logger, String format, Object[] params) {

        }

        @Override
        public void registerTracker(RequestProgressTracker tracker) {

        }
    };

    void log(String logger, String format, Object[] params);

    /**
     * Register the {@link RequestProgressTracker} associated with
     * current request
     * @param tracker from current request
     */
    void registerTracker(RequestProgressTracker tracker);
}
