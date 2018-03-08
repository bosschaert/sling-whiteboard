/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.feature.support.json;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Bundles;
import org.apache.sling.feature.Configuration;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.Feature;
import org.junit.Test;
import org.osgi.resource.Capability;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeatureJSONReaderTest {

    @Test public void testRead() throws Exception {
        final Feature feature = U.readFeature("test");
        assertNotNull(feature);
        assertNotNull(feature.getId());
        assertEquals("org.apache.sling", feature.getId().getGroupId());
        assertEquals("test-feature", feature.getId().getArtifactId());
        assertEquals("1.1", feature.getId().getVersion());
        assertEquals("jar", feature.getId().getType());
        assertNull(feature.getId().getClassifier());

        assertEquals(2, feature.getConfigurations().size());
        final Configuration cfg1 = U.findConfiguration(feature.getConfigurations(), "my.pid");
        assertEquals(7, cfg1.getProperties().get("number"));
        final Configuration cfg2 = U.findFactoryConfiguration(feature.getConfigurations(), "my.factory.pid", "name");
        assertEquals("yeah", cfg2.getProperties().get("a.value"));

        assertEquals(3, feature.getCapabilities().size());
        Capability capability = U.findCapability(feature.getCapabilities(),"osgi.service");
        assertNotNull(capability.getAttributes().get("objectClass"));

        assertEquals(Arrays.asList("org.osgi.service.http.runtime.HttpServiceRuntime"), capability.getAttributes().get("objectClass"));

    }

    @Test public void testReadWithVariables() throws Exception {
        final Feature feature = U.readFeature("test2");
        Bundles bundles = feature.getBundles();
        ArtifactId id = new ArtifactId("org.apache.sling", "foo-xyz", "1.2.3", null, null);
        assertTrue(bundles.containsExact(id));

    }

    @Test public void testReadRepoInitExtension() throws Exception {
        Feature feature = U.readFeature("repoinit");
        Extensions extensions = feature.getExtensions();
        assertEquals(1, extensions.size());
        Extension ext = extensions.iterator().next();
        assertEquals("some repo init\ntext", ext.getText());
    }

    @Test public void testReadRepoInitExtensionArray() throws Exception {
        Feature feature = U.readFeature("repoinit2");
        Extensions extensions = feature.getExtensions();
        assertEquals(1, extensions.size());
        Extension ext = extensions.iterator().next();
        assertEquals("some repo init\ntext\n", ext.getText());
    }

    @Test public void testHandleVars() throws Exception {
        FeatureJSONReader reader = new FeatureJSONReader(null, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("var1", "bar");
        vars.put("varvariable", "${myvar}");
        vars.put("var.2", "2");
        setPrivateField(reader, "variables", vars);

        assertEquals("foobarfoo", reader.handleVars("foo${var1}foo"));
        assertEquals("barbarbar", reader.handleVars("${var1}${var1}${var1}"));
        assertEquals("${}test${myvar}2", reader.handleVars("${}test${varvariable}${var.2}"));
    }

    private void setPrivateField(Object obj, String name, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
