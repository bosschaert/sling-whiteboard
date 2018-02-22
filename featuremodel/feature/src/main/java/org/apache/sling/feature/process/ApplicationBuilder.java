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
package org.apache.sling.feature.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.Application;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;

/**
 * Build an application based on features.
 */
public class ApplicationBuilder {

    /**
     * Assemble an application based on the provided feature Ids.
     *
     * Upgrade features are only applied if the provided feature list
     * contains the feature to be upgraded. Otherwise the upgrade feature
     * is ignored.
     *
     * @param app The optional application to use as a base.
     * @param context The builder context
     * @param featureIds The feature ids
     * @return The application
     * throws IllegalArgumentException If context or featureIds is {@code null}
     * throws IllegalStateException If the provided ids are invalid, or the feature can't be provided
     */
    public static Application assemble(final Application app,
            final BuilderContext context,
            final FeatureResolver resolver,
            final String... featureIds) {
        if ( featureIds == null || context == null ) {
            throw new IllegalArgumentException("Features and/or context must not be null");
        }

        final Feature[] features = new Feature[featureIds.length];
        int index = 0;
        for(final String id : featureIds) {
            features[index] = context.getFeatureProvider().provide(ArtifactId.parse(id));
            if ( features[index] == null ) {
                throw new IllegalStateException("Unable to find included feature " + id);
            }
            index++;
        }
        return assemble(app, context, resolver, features);
    }

    /**
     * Assemble an application based on the provided features.
     *
     * Upgrade features are only applied if the provided feature list
     * contains the feature to be upgraded. Otherwise the upgrade feature
     * is ignored.
     *
     * @param app The optional application to use as a base.
     * @param context The builder context
     * @param resolver
     * @param features The features
     * @return The application
     * throws IllegalArgumentException If context or featureIds is {@code null}
     * throws IllegalStateException If a feature can't be provided
     */
    public static Application assemble(
            Application app,
            final BuilderContext context,
            final FeatureResolver resolver, final Feature... features) {
        if ( features == null || context == null ) {
            throw new IllegalArgumentException("Features and/or context must not be null");
        }

        if ( app == null ) {
            app = new Application();
        }

        // detect upgrades and created sorted feature list
        final Map<Feature, List<Feature>> upgrades = new HashMap<>();
        List<Feature> sortedFeatureList = new ArrayList<>();
        for(final Feature f : features) {
            if ( f.getUpgradeOf() != null ) {
                for(final Feature i : features) {
                    if ( i.getId().equals(f.getUpgradeOf()) ) {
                        List<Feature> u = upgrades.get(i);
                        if ( u == null ) {
                            u = new ArrayList<>();
                            upgrades.put(i, u);
                        }
                        u.add(f);
                        app.getFeatureIds().add(f.getId());
                        break;
                    }
                }
            } else {
                app.getFeatureIds().add(f.getId());
                sortedFeatureList.add(f);
            }
        }

        // process upgrades first
        for(final Map.Entry<Feature, List<Feature>> entry : upgrades.entrySet()) {
            final Feature assembled = FeatureBuilder.assemble(entry.getKey(),
                    entry.getValue(),
                    context);
            // update feature to assembled feature
            sortedFeatureList.remove(entry.getKey());
            sortedFeatureList.add(assembled);
        }

        // order by dependency chain
        sortedFeatureList = resolver.orderFeatures(sortedFeatureList);

        // assemble
        for(final Feature f : sortedFeatureList) {
            final Feature assembled = FeatureBuilder.assemble(f, context.clone(new FeatureProvider() {

                @Override
                public Feature provide(final ArtifactId id) {
                    for(final Feature f : upgrades.keySet()) {
                        if ( f.getId().equals(id) ) {
                            return f;
                        }
                    }
                    for(final Feature f : features) {
                        if ( f.getId().equals(id) ) {
                            return f;
                        }
                    }
                    return context.getFeatureProvider().provide(id);
                }
            }));

            merge(app, assembled);
        }

        return app;
    }

    private static void merge(final Application target, final Feature source) {
        BuilderUtil.mergeBundles(target.getBundles(), source.getBundles(), BuilderUtil.ArtifactMerge.HIGHEST);
        BuilderUtil.mergeConfigurations(target.getConfigurations(), source.getConfigurations());
        BuilderUtil.mergeFrameworkProperties(target.getFrameworkProperties(), source.getFrameworkProperties());
        BuilderUtil.mergeExtensions(target, source, BuilderUtil.ArtifactMerge.HIGHEST);
    }
}
