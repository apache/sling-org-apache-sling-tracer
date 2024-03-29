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

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.webconsole.SimpleWebConsolePlugin;
import org.osgi.framework.BundleContext;

class TracerLogServlet extends SimpleWebConsolePlugin implements TraceLogRecorder {
    static final String ATTR_RECORDING = TracerLogServlet.class.getName();

    public static final String CLEAR = "clear";

    private static final String LABEL = "tracer";

    public static final String HEADER_TRACER_RECORDING = "Sling-Tracer-Record";

    public static final String HEADER_TRACER_REQUEST_ID = "Sling-Tracer-Request-Id";

    public static final String HEADER_TRACER_PROTOCOL_VERSION = "Sling-Tracer-Protocol-Version";

    public static final int TRACER_PROTOCOL_VERSION = 1;

    private final BoundedCache cache;

    private final boolean compressRecording;

    private final int cacheSizeInMB;

    private final long cacheDurationInSecs;

    private final boolean gzipResponse;

    public TracerLogServlet(BundleContext context, int cacheSizeInMB, long cacheDurationInSecs,
                            boolean compressionEnabled, boolean gzipResponse) {
        super(LABEL, "Sling Tracer", "Sling", null);
        this.compressRecording = compressionEnabled;
        this.cacheDurationInSecs = cacheDurationInSecs;
        this.cacheSizeInMB = cacheSizeInMB;
        this.gzipResponse = compressionEnabled && gzipResponse;
        this.cache = new BoundedCache(cacheSizeInMB, cacheDurationInSecs);
        register(context);
    }

    boolean isCompressRecording() {
        return compressRecording;
    }

    public boolean isGzipResponse() {
        return gzipResponse;
    }

    int getCacheSizeInMB() {
        return cacheSizeInMB;
    }

    long getCacheDurationInSecs() {
        return cacheDurationInSecs;
    }

    public static class BoundedCache {

        public static class Entry implements Comparable<Entry> {
            long lastAccessed;
            JSONRecording recording;

            @Override
            public int compareTo(Entry o) {
                return Long.compare(this.lastAccessed, o.lastAccessed);
            }
        }

        private final Map<String, Entry> cache = new HashMap<>();

        private final long maxSize;

        private volatile long currentSize;

        private final long cacheDurationInMillis;

        public BoundedCache(final long maxSizeInMB, final long cacheDurationInSecs) {
            this.maxSize = maxSizeInMB * 1024 * 1024;
            this.cacheDurationInMillis = TimeUnit.SECONDS.toMillis(cacheDurationInSecs);
        }

        public synchronized JSONRecording get(final String requestId) {
            checkCache();
            final Entry entry = this.cache.get(requestId);
            if (entry != null) {
                entry.lastAccessed = System.currentTimeMillis();
                cache.put(requestId, entry);
                return entry.recording;
            }
            return null;
        }

        public synchronized void put(final String requestId, final JSONRecording recording) {
            final Entry entry = new Entry();
            entry.lastAccessed = System.currentTimeMillis();
            entry.recording = recording;
            cache.put(requestId, entry);
            this.currentSize += recording.size();
            checkCache();
        }

        private void checkCache() {
            final Set<String> toRemove = new HashSet<>();
            final ArrayList<Entry> list = new ArrayList<>(this.cache.values());
            Collections.sort(list);
            if ( this.currentSize > this.maxSize ) {
                while (this.currentSize > this.maxSize && !list.isEmpty()) {
                    final Entry entry = list.remove(0);
                    toRemove.add(entry.recording.getRequestId());
                    currentSize -= entry.recording.size();
                }
            }
            final long time = System.currentTimeMillis() - this.cacheDurationInMillis;
            for(final Entry entry : list) {
                if ( entry.lastAccessed < time ) {
                    toRemove.add(entry.recording.getRequestId());
                    currentSize -= entry.recording.size();
                }
            }
            for(final String key : toRemove) {
                this.cache.remove(key);
            }
        }

        public synchronized int size() {
            checkCache();
            return this.cache.size();
        }

        public synchronized long memorySize() {
            checkCache();
            return this.currentSize;
        }

        public synchronized void clear() {
            this.cache.clear();
            this.currentSize = 0;
        }

        public synchronized List<JSONRecording> asList() {
            checkCache();
            final List<JSONRecording> result = new ArrayList<>();
            for(final Entry entry : this.cache.values()) {
                result.add(entry.recording);
            }
            return result;
        }
    }

    //~-----------------------------------------------< WebConsole Plugin >

    @Override
    protected void renderContent(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (isHtmlRequest(request)){
            PrintWriter pw = response.getWriter();
            renderStatus(pw);
            renderRequests(pw);
        } else {
            String requestId = getRequestId(request);
            prepareJSONResponse(response);
            try {
                boolean responseDone = false;
                if (requestId != null) {
                    JSONRecording recording = cache.get(requestId);
                    if (recording != null){
                        boolean shouldGZip = prepareForGZipResponse(request, response);
                        responseDone = recording.render(response.getOutputStream(), shouldGZip);
                    }
                }

                if (!responseDone) {
                    PrintWriter pw = response.getWriter();
                    JSONWriter jw = new JSONWriter(pw);
                    jw.object();
                    jw.key("error").value("Not found");
                    jw.endObject();
                }
            } catch (IOException e) {
                throw new ServletException(e);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getParameter(CLEAR) != null) {
            resetCache();
            resp.sendRedirect(req.getRequestURI());
        }
    }

    @Override
    protected boolean isHtmlRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith(LABEL);
    }

    private boolean prepareForGZipResponse(HttpServletRequest request, HttpServletResponse response) {
        if (!gzipResponse) {
            return false;
        }

        String acceptEncoding = request.getHeader("Accept-Encoding");
        boolean acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");

        if (acceptsGzip) {
            response.setHeader("Content-Encoding", "gzip");
        }
        return acceptsGzip;
    }

    /**
     * Returns true if the given accept header accepts the given value.
     * @param acceptHeader The accept header.
     * @param toAccept The value to be accepted.
     * @return True if the given accept header accepts the given value.
     */
    private static boolean accepts(String acceptHeader, String toAccept) {
        return acceptHeader.contains(toAccept) || acceptHeader.contains("*/*");
    }

    private static void prepareJSONResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
    }

    private void renderStatus(PrintWriter pw) {
        pw.printf("<p class='statline'>Log Tracer Recordings: %d recordings, %s memory " +
                "(Max %dMB, Expired in %d secs)</p>%n", cache.size(),
                memorySize(), cacheSizeInMB, cacheDurationInSecs);

        pw.println("<div class='ui-widget-header ui-corner-top buttonGroup'>");
        pw.println("<span style='float: left; margin-left: 1em'>Tracer Recordings</span>");
        pw.println("<form method='POST'><input type='hidden' name='clear' value='clear'><input type='submit' value='Clear' class='ui-state-default ui-corner-all'></form>");
        pw.println("</div>");
    }

    private String memorySize() {
        return humanReadableByteCount(cache.memorySize());
    }

    private void renderRequests(PrintWriter pw) {
        if (cache.size() > 0){
            pw.println("<ol>");
            List<JSONRecording> recordings = cache.asList();
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            Collections.sort(recordings);
            for (JSONRecording r : recordings){
                String id = r.getRequestId();
                String date = sdf.format(new Date(r.getStart()));
                pw.printf("<li>%s - <a href='%s/%s.json'>%s</a> - %s (%s) (%dms)</li>",
                        date, LABEL, id, id,
                        r.getUri(),
                        humanReadableByteCount(r.size()),
                        r.getTimeTaken());
            }
            pw.println("</ol>");
        }
    }

    private static String getRequestId(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        int lastSlash = requestUri.lastIndexOf('/');
        int lastDot = requestUri.indexOf('.', lastSlash + 1);
        if (lastDot > 0){
            return requestUri.substring(lastSlash + 1, lastDot);
        }
        return null;
    }

    //~-----------------------------------------------< TraceLogRecorder >

    @Override
    public Recording startRecording(HttpServletRequest request, HttpServletResponse response) {
        if (request.getHeader(HEADER_TRACER_RECORDING) == null){
            return Recording.NOOP;
        }

        if (request.getAttribute(ATTR_RECORDING) != null){
            //Already processed
            return getRecordingForRequest(request);
        }

        String requestId = generateRequestId();
        JSONRecording recording = record(requestId, request);

        response.setHeader(HEADER_TRACER_REQUEST_ID, requestId);
        response.setHeader(HEADER_TRACER_PROTOCOL_VERSION, String.valueOf(TRACER_PROTOCOL_VERSION));

        return recording;
    }

    @Override
    public Recording getRecordingForRequest(HttpServletRequest request) {
        Recording recording = (Recording) request.getAttribute(ATTR_RECORDING);
        if (recording == null){
            recording = Recording.NOOP;
        }
        return recording;
    }

    @Override
    public void endRecording(HttpServletRequest httpRequest, Recording recording) {
        if (recording instanceof JSONRecording) {
            JSONRecording r = (JSONRecording) recording;
            r.done();
            cache.put(r.getRequestId(), r);
        }
        httpRequest.removeAttribute(ATTR_RECORDING);
    }

    Recording getRecording(String requestId) {
        Recording recording = cache.get(requestId);
        return recording == null ? Recording.NOOP : recording;
    }

    private JSONRecording record(String requestId, HttpServletRequest request) {
        JSONRecording data = new JSONRecording(requestId, request, compressRecording);
        request.setAttribute(ATTR_RECORDING, data);
        return data;
    }

    private static String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns a human-readable version of the file size, where the input represents
     * a specific number of bytes. Based on http://stackoverflow.com/a/3758880/1035417
     */
    @SuppressWarnings("Duplicates")
    private static String humanReadableByteCount(long bytes) {
        if (bytes < 0) {
            return "0";
        }
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "kMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    void resetCache(){
        cache.clear();
    }
}
