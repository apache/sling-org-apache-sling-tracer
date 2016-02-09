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

import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;

class JSONRecording implements Recording {
    private final String method;
    private final List<String> queries = new ArrayList<String>();
    private RequestProgressTracker tracker;

    public JSONRecording(HttpServletRequest r) {
        this.method = r.getMethod();
    }

    public void render(Writer pw) throws JSONException {
        JSONWriter jw = new JSONWriter(pw);
        jw.setTidy(true);
        jw.object();
        jw.key("method").value(method);

        if (tracker != null) {
            jw.key("logs");
            jw.array();
            Iterator<String> it = tracker.getMessages();
            //Per docs iterator can be null
            while (it != null && it.hasNext()) {
                jw.value(it.next());
            }
            jw.endArray();
        }

        jw.key("queries");
        jw.array();
        for (String q : queries) {
            jw.value(q);
        }
        jw.endArray();
        jw.endObject();
    }

    //~---------------------------------------< Recording >

    @Override
    public void log(String logger, String format, Object[] params) {
        if (TracerContext.QUERY_LOGGER.equals(logger)
                && params != null && params.length == 2) {
            queries.add((String) params[1]);
        }
    }

    @Override
    public void registerTracker(RequestProgressTracker tracker) {
        this.tracker = tracker;
    }

    RequestProgressTracker getTracker() {
        return tracker;
    }
}