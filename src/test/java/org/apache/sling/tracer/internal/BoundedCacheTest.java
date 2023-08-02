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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class BoundedCacheTest {
  
    @Test public void testMemoryBoundary() {
        final TracerLogServlet.BoundedCache cache = new TracerLogServlet.BoundedCache(2, 10);
        JSONRecording recordingA = Mockito.mock(JSONRecording.class);
        Mockito.when(recordingA.size()).thenReturn(1*1024*1024);
        Mockito.when(recordingA.getRequestId()).thenReturn("a");
        JSONRecording recordingB = Mockito.mock(JSONRecording.class);
        Mockito.when(recordingB.size()).thenReturn(1*1000*1000);
        Mockito.when(recordingB.getRequestId()).thenReturn("b");

        cache.put("a", recordingA);
        cache.put("b", recordingB);

        Assert.assertEquals(recordingA.size() + recordingB.size(), cache.memorySize());
        Assert.assertNotNull(cache.get("a"));
        Assert.assertNotNull(cache.get("b"));

        JSONRecording recordingC = Mockito.mock(JSONRecording.class);
        Mockito.when(recordingC.size()).thenReturn(1*1024*1024);
        Mockito.when(recordingC.getRequestId()).thenReturn("c");
        cache.put("c", recordingC);

        Assert.assertNull(cache.get("a"));
        Assert.assertNotNull(cache.get("b"));
        Assert.assertNotNull(cache.get("c"));
        Assert.assertEquals(recordingB.size() + recordingC.size(), cache.memorySize());
    }

    @Test public void testTimeBoundary() throws InterruptedException {
        final TracerLogServlet.BoundedCache cache = new TracerLogServlet.BoundedCache(2, 1);
        JSONRecording recordingA = Mockito.mock(JSONRecording.class);
        Mockito.when(recordingA.size()).thenReturn(1*1024*1024);
        Mockito.when(recordingA.getRequestId()).thenReturn("a");
        JSONRecording recordingB = Mockito.mock(JSONRecording.class);
        Mockito.when(recordingB.size()).thenReturn(1*1000*1000);
        Mockito.when(recordingB.getRequestId()).thenReturn("b");

        cache.put("a", recordingA);
        cache.put("b", recordingB);

        Thread.sleep(2000); // cache should be empty now
        Assert.assertEquals(0L, cache.memorySize());
        Assert.assertNull(cache.get("a"));
        Assert.assertNull(cache.get("b"));
        Assert.assertEquals(0, cache.size());
    }
}
