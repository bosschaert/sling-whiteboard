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
package org.apache.sling.feature.modelconverter.impl;

import org.apache.sling.feature.Application;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Bundles;
import org.apache.sling.feature.Configurations;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.Extensions;
import org.apache.sling.feature.KeyValueMap;
import org.apache.sling.feature.process.FeatureResolver;
import org.apache.sling.feature.support.ArtifactManager;
import org.apache.sling.feature.support.FeatureUtil;
import org.apache.sling.feature.support.json.ApplicationJSONReader;
import org.apache.sling.provisioning.model.Artifact;
import org.apache.sling.provisioning.model.Configuration;
import org.apache.sling.provisioning.model.Feature;
import org.apache.sling.provisioning.model.Model;
import org.apache.sling.provisioning.model.Section;
import org.apache.sling.provisioning.model.io.ModelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public class FeatureToProvisioning {
    private static Logger LOGGER = LoggerFactory.getLogger(FeatureToProvisioning.class);

    public static void convert(File file, String output, ArtifactManager am) throws IOException {
        org.apache.sling.feature.Feature feature = FeatureUtil.getFeature(file.getAbsolutePath(), am);

        String featureName;
        if (feature.getTitle() != null) {
            featureName = feature.getTitle();
        } else {
            featureName = feature.getId().getArtifactId();
        }

        Feature newFeature = new Feature(featureName);
        convert(newFeature, feature.getBundles(), feature.getConfigurations(), feature.getFrameworkProperties(), feature.getExtensions(), output);
    }

    public static void convert(List<File> files, String output, boolean createApp, ArtifactManager am) throws Exception {
        try (FeatureResolver fr = null) { // TODO we could use the resolver: new FrameworkResolver(am)
            if ( createApp ) {
                // each file is an application
                int index = 1;
                for(final File appFile : files ) {
                    try ( final FileReader r = new FileReader(appFile) ) {
                        final Application app = ApplicationJSONReader.read(r);
                        FeatureToProvisioning.convert(app, files.size() > 1 ? index : 0, output);
                    }
                    index++;
                }
            } else {
                final Application app = FeatureUtil.assembleApplication(null, am, fr, files.stream()
                        .map(File::getAbsolutePath)
                        .toArray(String[]::new));
                FeatureToProvisioning.convert(app, 0, output);
            }
        }
    }

    private static void convert(final Application app, final int index, final String outputFile) {
        String featureName;

        List<ArtifactId> fids = app.getFeatureIds();
        if (fids.size() > 0) {
            featureName = fids.get(0).getArtifactId();
        } else {
            featureName = "application";
        }
        final Feature feature = new Feature(featureName);

        convert(feature, app.getBundles(), app.getConfigurations(), app.getFrameworkProperties(), app.getExtensions(), outputFile);
    }

    private static void convert(Feature f, Bundles bundles, Configurations configurations, KeyValueMap frameworkProps,
            Extensions extensions, String outputFile) {
        // bundles
        for(final org.apache.sling.feature.Artifact bundle : bundles) {
            final ArtifactId id = bundle.getId();
            final Artifact newBundle = new Artifact(id.getGroupId(), id.getArtifactId(), id.getVersion(), id.getClassifier(), id.getType());
            for(final Map.Entry<String, String> prop : bundle.getMetadata()) {
                switch (prop.getKey()) {
                    // these are handled separately
                    case "start-level":
                    case "run-mode":
                        break;
                    default:
                        newBundle.getMetadata().put(prop.getKey(), prop.getValue());
                }
            }

            int startLevel;
            String sl = bundle.getMetadata().get("start-level");
            if (sl != null) {
                startLevel = Integer.parseInt(sl);
            } else {
                startLevel = 20;
            }

            String runMode = bundle.getMetadata().get("run-mode");
            String[] runModes;
            if (runMode != null) {
                runModes = runMode.split(",");
            } else {
                runModes = null;
            }
            f.getOrCreateRunMode(runModes).getOrCreateArtifactGroup(startLevel).add(newBundle);
        }

        // configurations
        for(final org.apache.sling.feature.Configuration cfg : configurations) {
            final Configuration c;
            if ( cfg.isFactoryConfiguration() ) {
                c = new Configuration(cfg.getName(), cfg.getFactoryPid());
            } else {
                c = new Configuration(cfg.getPid(), null);
            }
            final Enumeration<String> keys = cfg.getProperties().keys();
            while ( keys.hasMoreElements() ) {
                final String key = keys.nextElement();
                c.getProperties().put(key, cfg.getProperties().get(key));
            }
            f.getOrCreateRunMode(null).getConfigurations().add(c);
        }

        // framework properties
        for(final Map.Entry<String, String> prop : frameworkProps) {
            f.getOrCreateRunMode(null).getSettings().put(prop.getKey(), prop.getValue());
        }

        // extensions: content packages and repoinit
        for(final Extension ext : extensions) {
            if ( Extension.NAME_CONTENT_PACKAGES.equals(ext.getName()) ) {
                for(final org.apache.sling.feature.Artifact cp : ext.getArtifacts() ) {
                    final ArtifactId id = cp.getId();
                    final Artifact newCP = new Artifact(id.getGroupId(), id.getArtifactId(), id.getVersion(), id.getClassifier(), id.getType());
                    for(final Map.Entry<String, String> prop : cp.getMetadata()) {
                        newCP.getMetadata().put(prop.getKey(), prop.getValue());
                    }
                    f.getOrCreateRunMode(null).getOrCreateArtifactGroup(0).add(newCP);
                }

            } else if ( Extension.NAME_REPOINIT.equals(ext.getName()) ) {
                final Section section = new Section("repoinit");
                section.setContents(ext.getText());
                f.getAdditionalSections().add(section);
            } else if ( ext.isRequired() ) {
                LOGGER.error("Unable to convert required extension {}", ext.getName());
                System.exit(1);
            }
        }

        LOGGER.info("Writing feature...");
        final String out = outputFile;
        final File file = new File(out);
        final Model m = new Model();
        m.getFeatures().add(f);
        try ( final FileWriter writer = new FileWriter(file)) {
            ModelWriter.write(writer, m);
        } catch ( final IOException ioe) {
            LOGGER.error("Unable to write feature to {} : {}", out, ioe.getMessage(), ioe);
            System.exit(1);
        }
    }
}
