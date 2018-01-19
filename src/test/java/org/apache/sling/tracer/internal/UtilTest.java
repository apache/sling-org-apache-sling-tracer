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

import org.junit.Test;

import static org.junit.Assert.*;

public class UtilTest {

    @Test
    public void trimToNull() throws Exception{
        assertEquals(null, Util.trimToNull(null));
        assertEquals(null, Util.trimToNull(""));
        assertEquals("foo", Util.trimToNull("foo"));
        assertEquals("foo", Util.trimToNull(" foo"));
    }

    @Test
    public void nullSafeString() throws Exception{
        assertEquals(null, Util.nullSafeString(null));
        assertEquals("foo", Util.nullSafeString("foo"));
        assertEquals("1", Util.nullSafeString(1));
    }

    @Test
    public void nullSafeTrim() throws Exception{
        assertEquals("", Util.nullSafeTrim(null));
        assertEquals("foo", Util.nullSafeTrim(" foo"));
    }

    @Test
    public void count() throws Exception{
        assertEquals(2, Util.count("she sell sea shells on the sea shore", "sea"));
        assertEquals(1, Util.count("she sell sea shells on the sea shore", "shore"));
        assertEquals(0, Util.count("she sell sea shells on the sea shore", "tiger"));
    }

}