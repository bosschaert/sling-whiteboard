/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.resolver;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.process.FeatureResolver;
import org.apache.sling.feature.support.ArtifactHandler;
import org.apache.sling.feature.support.ArtifactManager;
import org.apache.sling.feature.support.ArtifactManagerConfig;
import org.apache.sling.feature.support.json.FeatureJSONReader;
import org.junit.Test;

public class FrameworkResolverTest {
    @Test
    public void testResolveEmptyFeatureList() throws Exception {
        ArtifactManager am = ArtifactManager.getArtifactManager(new ArtifactManagerConfig());
        try (FeatureResolver fr = new FrameworkResolver(am)) {
            assertEquals(Collections.emptyList(),
                    fr.orderFeatures(Collections.emptyList()));
        }
    }

    @Test
    public void testOrderFeatures() throws Exception {
        ArtifactManager am = ArtifactManager.getArtifactManager(new ArtifactManagerConfig());

        Feature f1 = readFeature("/feature1.json", am);
        Feature f2 = readFeature("/feature2.json", am);
        Feature f3 = readFeature("/feature3.json", am);

        try (FeatureResolver fr = new FrameworkResolver(am)) {
            List<Feature> ordered = fr.orderFeatures(Arrays.asList(f1, f2, f3));
            List<Feature> expected = Arrays.asList(f3, f2, f1);
            assertEquals(expected, ordered);
        }
    }

    private Feature readFeature(final String res,
            final ArtifactManager artifactManager) throws Exception {
        URL url = getClass().getResource(res);
        String file = new File(url.toURI()).getAbsolutePath();
        final ArtifactHandler featureArtifact = artifactManager.getArtifactHandler(file);

        try (final FileReader r = new FileReader(featureArtifact.getFile())) {
            final Feature f = FeatureJSONReader.read(r, featureArtifact.getUrl());
            return f;
        }
    }
}
