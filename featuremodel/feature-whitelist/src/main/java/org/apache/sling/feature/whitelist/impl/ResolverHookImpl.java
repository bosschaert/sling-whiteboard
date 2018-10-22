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
package org.apache.sling.feature.whitelist.impl;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

class ResolverHookImpl implements ResolverHook {
    String idbsnverFileName = "idbsnver.properties";
    String bundleFeatureFileName = "bundles.properties";
    String regionPackageFileName = "regions.properties";
    String featureRegionFileName = "features.properties";

    final Map<Map.Entry<String, Version>, List<String>> bsnVerMap;
    final Map<String, List<String>> bundleFeatureMap;
    final Map<String, Set<String>> featureRegionMap;
    final Map<String, Set<String>> regionPackageMap;

//    private static final long SERVICE_WAIT_TIMEOUT = 60000;
//
//    private final ServiceTracker<Features, Features> featureServiceTracker;
//    private final WhitelistService whitelistService;
//
//    @Reference
//    Bundles bundleService;
//
//    @Reference
//    Features featuresService;
//
    public ResolverHookImpl() throws IOException {
//        featureServiceTracker = tracker;
//        whitelistService = wls;
        bsnVerMap = populateBSNVerMap();
        bundleFeatureMap = populateBundleFeatureMap();


    }

    private Map<Map.Entry<String, Version>, List<String>> populateBSNVerMap() throws IOException, FileNotFoundException {
        File idbsnverFile = getDataFile(idbsnverFileName);
        if (idbsnverFile.exists()) {
            Map<Map.Entry<String, Version>, List<String>> m = new HashMap<>();

            Properties p = new Properties();
            try (InputStream is = new FileInputStream(idbsnverFile)) {
                p.load(is);
            }

            for (String n : p.stringPropertyNames()) {
                String[] bsnver = p.getProperty(n).split("~");
                Map.Entry<String, Version> key = new AbstractMap.SimpleEntry<String, Version>(bsnver[0], Version.valueOf(bsnver[1]));
                List<String> l = m.get(key);
                if (l == null) {
                    l = new ArrayList<>();
                    m.put(key, l);
                }
            }

            Map<Map.Entry<String, Version>, List<String>> m2 = new HashMap<>();

            for (Map.Entry<Map.Entry<String, Version>, List<String>> entry : m.entrySet()) {
                m2.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }

            return Collections.unmodifiableMap(m2);
        } else {
            return null;
        }
    }

    private Map<String, List<String>> populateBundleFeatureMap() {
        // TODO Auto-generated method stub
        return null;
    }

    private File getDataFile(String name) {
        String dir = System.getProperty("launcher.framework.datadir");
        if (dir == null)
            return null;

        return new File(dir, name);
    }

    @Override
    public void filterResolvable(Collection<BundleRevision> candidates) {
        // Nothing to do
    }

    @Override
    public void filterSingletonCollisions(BundleCapability singleton, Collection<BundleCapability> collisionCandidates) {
        // Nothing to do
    }

    @Override
    public void filterMatches(BundleRequirement requirement, Collection<BundleCapability> candidates) {
        // Filtering is only on package resolution. Any other kind of resolution is not limited
        if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace()))
            return;

        System.out.println("*** Filter Matches: " + requirement);
        Bundle reqBundle = requirement.getRevision().getBundle();
        long reqBundleID = reqBundle.getBundleId();
        String reqBundleName = reqBundle.getSymbolicName();
        Version reqBundleVersion = reqBundle.getVersion();

        try {
            /*
            Features fs = featureServiceTracker.waitForService(SERVICE_WAIT_TIMEOUT);

            // The Feature Service could not be found, skip candidate pruning
            if (fs == null) {
                WhitelistEnforcer.LOG.warning("Could not obtain the feature service, no whitelist enforcement");
                return;
            }
            */

//            String reqBundleArtifact = bundleService.getBundleArtifact(reqBundleName, reqBundleVersion.toString());
            List<String> aids = bsnVerMap.get(new AbstractMap.SimpleEntry<String, Version>(reqBundleName, reqBundleVersion));
            if (aids == null)
                return; // TODO what to do?
            List<String> reqFeatures = new ArrayList<>();
            for (String aid : aids) {
                List<String> fid = bundleFeatureMap.get(aid);
                if (fid != null)
                    reqFeatures.addAll(fid);
            }
//            String reqFeatureArtifact = featuresService.getBundleOrigin(ArtifactId.fromMvnId(reqBundleArtifact));
//            Set<String> reqFeatures = Collections.singleton(reqFeatureArtifact); // TODO multiple features?
            /*
            Set<String> regions;
            if (reqFeatures == null) {
                regions = Collections.emptySet();
                reqFeatures = Collections.emptySet();
            } else {
                regions = new HashSet<>();
                for (String feature : reqFeatures) {
                    regions.addAll(whitelistService.listRegions(feature));
                }
            }
            */
            Set<String> regions = new HashSet<>();
            for (String feature : reqFeatures) {
                Set<String> fr = featureRegionMap.get(feature);
                if (fr != null) {
                    regions.addAll(fr);
                }
            }

            Set<BundleCapability> coveredCaps = new HashSet<>();

            nextCapability:
            for (BundleCapability bc : candidates) {
                BundleRevision rev = bc.getRevision();

                Bundle capBundle = rev.getBundle();
                long capBundleID = capBundle.getBundleId();
                if (capBundleID == 0) {
                    // always allow capability from the system bundle
                    coveredCaps.add(bc);
                    continue nextCapability;
                }

                if (capBundleID == reqBundleID) {
                    // always allow capability from same bundle
                    coveredCaps.add(bc);
                    continue nextCapability;
                }

                String capBundleName = capBundle.getSymbolicName();
                Version capBundleVersion = capBundle.getVersion();

                /*
                String capBundleArtifact = bundleService.getBundleArtifact(capBundleName, capBundleVersion.toString());
                String capFeatureArtifact = featuresService.getBundleOrigin(ArtifactId.fromMvnId(capBundleArtifact));
                Set<String> capFeatures = Collections.singleton(capFeatureArtifact); // TODO multiple features?
                */
                List<String> capBundleArtifacts = bsnVerMap.get(new AbstractMap.SimpleEntry<String, Version>(capBundleName, capBundleVersion));
                if (capBundleArtifacts == null)
                    return; // TODO what to do?
                List<String> capFeatures = new ArrayList<>();
                for (String ba : capBundleArtifacts) {
                    List<String> capfeats = bundleFeatureMap.get(ba);
                    if (capfeats != null)
                        capFeatures.addAll(capfeats);
                }

                if (capFeatures.isEmpty())
                    capFeatures = Collections.singletonList(null);

                for (String capFeat : capFeatures) {
                    if (capFeat == null) {
                        // always allow capability not coming from a feature
                        coveredCaps.add(bc);
                        continue nextCapability;
                    }

                    if (reqFeatures.contains(capFeat)) {
                        // Within a single feature everything can wire to everything else
                        coveredCaps.add(bc);
                        continue nextCapability;
                    }

//                    if (whitelistService.listRegions(capFeat) == null) {
                    if (featureRegionMap.get(capFeat) == null) {
                        // If the feature hosting the capability has no regions defined, everyone can access
                        coveredCaps.add(bc);
                        continue nextCapability;
                    }

                    Object pkg = bc.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE);
                    if (pkg instanceof String) {
                        String packageName = (String) pkg;

//                        if (whitelistService.listPackages(WhitelistService.GLOBAL_REGION).contains(packageName)) {
                        Set<String> globalPackages = regionPackageMap.get("global");
                        if (globalPackages != null && globalPackages.contains(packageName)) {
                            // If the export is in the global region everyone can access
                            coveredCaps.add(bc);
                            continue nextCapability;
                        }

                        for (String region : regions) {
//                            if (whitelistService.listPackages(region).contains(packageName)) {
                            Set<String> regionPackages = regionPackageMap.get(region);
                            if (regionPackages != null && regionPackages.contains(packageName)) {
                                // If the export is in a region that the feature is also in, then allow
                                coveredCaps.add(bc);
                                continue nextCapability;
                            }
                        }
                    }
                }
            }

            // Remove any capabilities that are not covered
            if (candidates.retainAll(coveredCaps)) {
                WhitelistEnforcer.LOG.log(Level.INFO,
                        "Removed one ore more candidates for requirement {0} as they are not in the correct region", requirement);
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    public void end() {
        // Nothing to do
    }
}
