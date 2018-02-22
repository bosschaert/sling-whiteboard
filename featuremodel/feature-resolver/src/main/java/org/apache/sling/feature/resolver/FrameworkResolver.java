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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.BundleDescriptor;
import org.apache.sling.feature.analyser.impl.BundleDescriptorImpl;
import org.apache.sling.feature.process.FeatureResolver;
import org.apache.sling.feature.resolver.impl.ResolveContextImpl;
import org.apache.sling.feature.resolver.impl.ResourceImpl;
import org.apache.sling.feature.support.ArtifactManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.Resolver;

public class FrameworkResolver implements FeatureResolver {
    private final ArtifactManager artifactManager;
    private final Resolver resolver;
    private final Resource frameworkResource;
    private final Framework framework;

    public FrameworkResolver(ArtifactManager am) {
        artifactManager = am;

        Resolver r = null;
        // Launch an OSGi framework and obtain its resolver
        try {
            FrameworkFactory fwf = ServiceLoader.load(FrameworkFactory.class).iterator().next();
            framework = fwf.newFramework(Collections.emptyMap());
            framework.init();
            framework.start();
            BundleContext ctx = framework.getBundleContext();

            // Create a resource representing the framework
            BundleRevision br = framework.adapt(BundleRevision.class);
            List<Capability> caps = br.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
            frameworkResource = new ResourceImpl("framework",
                    Collections.singletonMap(PackageNamespace.PACKAGE_NAMESPACE, caps), Collections.emptyMap());

            int i=0;
            while (i < 20) {
                ServiceReference<Resolver> ref = ctx.getServiceReference(Resolver.class);
                if (ref != null) {
                    r = ctx.getService(ref);
                    break;
                }

                // The service isn't there yet, let's wait a little and try again
                Thread.sleep(500);
                i++;
            }
        } catch (BundleException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        resolver = r;
    }

    @Override
    public void close() throws Exception {
        framework.stop();
    }

    @Override
    public List<Feature> orderFeatures(List<Feature> features) {
        try {
            return orderFeatures2(features);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<Feature> orderFeatures2(List<? extends Feature> features) throws IOException {
        Map<Resource, Feature> bundleMap = new HashMap<>();
        for (Feature f : features) {
            for (Artifact b : f.getBundles()) {
                BundleDescriptor bd = getBundleDescriptor(artifactManager, b);
                Resource r = new ResourceImpl(bd);
                bundleMap.put(r, f);
            }
        }

        // Add these to the available features
        Artifact lpa = new Artifact(ArtifactId.parse("org.apache.sling/org.apache.sling.launchpad.api/1.2.0"));
        bundleMap.put(new ResourceImpl(getBundleDescriptor(artifactManager, lpa)), null);
        bundleMap.put(frameworkResource, null);

        List<Resource> orderedBundles = new LinkedList<>();
        try {
            for (Resource bundle : bundleMap.keySet()) {
                if (orderedBundles.contains(bundle)) {
                    // Already handled
                    continue;
                }
                Map<Resource, List<Wire>> deps = resolver.resolve(new ResolveContextImpl(bundle, bundleMap.keySet()));

                for (Map.Entry<Resource, List<Wire>> entry : deps.entrySet()) {
                    Resource curBundle = entry.getKey();

                    if (!bundleMap.containsKey(curBundle)) {
                        // This is some synthesized bundle. Ignoring.
                        continue;
                    }

                    if (!orderedBundles.contains(curBundle)) {
                        orderedBundles.add(curBundle);
                    }

                    for (Wire w : entry.getValue()) {
                        Resource provBundle = w.getProvider();
                        int curBundleIdx = orderedBundles.indexOf(curBundle);
                        int newBundleIdx = orderedBundles.indexOf(provBundle);
                        if (newBundleIdx >= 0) {
                            if (curBundleIdx < newBundleIdx) {
                                // If the list already contains the providing but after the current bundle, remove it there to move it before the current bundle
                                orderedBundles.remove(provBundle);
                            } else {
                                // If the providing bundle is already before the current bundle, then no need to change anything
                                continue;
                            }
                        }
                        orderedBundles.add(curBundleIdx, provBundle);
                    }
                }
            }
        } catch (ResolutionException e) {
            throw new RuntimeException(e);
        }

        // Sort the fragments so that fragments are started before the host bundle
        for (int i=0; i<orderedBundles.size(); i++) {
            Resource r = orderedBundles.get(i);
            List<Requirement> reqs = r.getRequirements(HostNamespace.HOST_NAMESPACE);
            if (reqs.size() > 0) {
                // This is a fragment
                Requirement req = reqs.iterator().next(); // TODO handle more host requirements
                String bsn = req.getAttributes().get(HostNamespace.HOST_NAMESPACE).toString(); // TODO this is not valid, should obtain from filter
                int idx = getBundleIndex(orderedBundles, bsn); // TODO check for filter too
                if (idx < i) {
                    // the fragment is after the host, and should be moved to be before the host
                    Resource frag = orderedBundles.remove(i);
                    orderedBundles.add(idx, frag);
                }
            }
        }

        List<Feature> orderedFeatures = new ArrayList<>();
        for (Resource r : orderedBundles) {
            Feature f = bundleMap.get(r);
            if (f != null) {
                if (!orderedFeatures.contains(f)) {
                    orderedFeatures.add(f);
                }
            }
        }
        return orderedFeatures;
    }

    private static int getBundleIndex(List<Resource> bundles, String bundleSymbolicName) {
        for (int i=0; i<bundles.size(); i++) {
            Resource b = bundles.get(i);
            if (bundleSymbolicName.equals(getBundleSymbolicName(b))) {
                return i;
            }
        }
        return -1;
    }

    private static String getBundleSymbolicName(Resource b) {
        for (Capability cap : b.getCapabilities(BundleNamespace.BUNDLE_NAMESPACE)) {
            return cap.getAttributes().get(BundleNamespace.BUNDLE_NAMESPACE).toString();
        }
        return null;
    }

    private static BundleDescriptor getBundleDescriptor(ArtifactManager artifactManager, Artifact b) throws IOException {
        final File file = artifactManager.getArtifactHandler(b.getId().toMvnUrl()).getFile();
        if ( file == null ) {
            throw new IOException("Unable to find file for " + b.getId());
        }

        return new BundleDescriptorImpl(b, file, -1);
    }
}
