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
package org.apache.sling.feature.support;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.sling.feature.Application;
import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.process.ApplicationBuilder;
import org.apache.sling.feature.process.BuilderContext;
import org.apache.sling.feature.process.FeatureProvider;
import org.apache.sling.feature.support.descriptor.BundleDescriptor;
import org.apache.sling.feature.support.descriptor.impl.BundleDescriptorImpl;
import org.apache.sling.feature.support.json.FeatureJSONReader;
import org.apache.sling.feature.support.resolver.ResolveContextImpl;
import org.apache.sling.feature.support.resolver.ResourceImpl;
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

public class FeatureUtil {
    private static Resolver resolver = loadResolver();
    private static Resource frameworkResource; // TODO properly initialize

    /**
     * Get an artifact id for the Apache Felix framework
     * @param version The version to use or {@code null} for the default version
     * @return The artifact id
     * @throws IllegalArgumentException If the provided version is invalid
     */
    public static ArtifactId getFelixFrameworkId(final String version) {
        return new ArtifactId("org.apache.felix",
                "org.apache.felix.framework",
                version != null ? version : "5.6.8", null, null);
    }

    static final Comparator<String> FEATURE_PATH_COMP = new Comparator<String>() {

        @Override
        public int compare(final String o1, final String o2) {
            // windows path conversion
            final String key1 = o1.replace(File.separatorChar, '/');
            final String key2 = o2.replace(File.separatorChar, '/');

            final int lastSlash1 = key1.lastIndexOf('/');
            final int lastSlash2 = key2.lastIndexOf('/');
            if ( lastSlash1 == -1 || lastSlash2 == -1 ) {
                return o1.compareTo(o2);
            }
            final String path1 = key1.substring(0, lastSlash1 + 1);
            final String path2 = key2.substring(0, lastSlash2 + 1);
            if ( path1.equals(path2) ) {
                return o1.compareTo(o2);
            }
            if ( path1.startsWith(path2) ) {
                return 1;
            } else if ( path2.startsWith(path1) ) {
                return -1;
            }
            return o1.compareTo(o2);
        }
    };

    private static void processDir(final List<String> paths, final File dir)
    throws IOException {
        for(final File f : dir.listFiles()) {
            if ( f.isFile() && !f.getName().startsWith(".")) {
                // check if file is a reference
                if ( f.getName().endsWith(".ref") || f.getName().endsWith(".json") ) {
                    processFile(paths, f);
                }
            }
        }
    }

    public static List<String> parseFeatureRefFile(final File file)
    throws IOException {
        final List<String> result = new ArrayList<>();
        final List<String> lines = Files.readAllLines(file.toPath());
        for(String line : lines) {
            line = line.trim();
            if ( !line.isEmpty() && !line.startsWith("#") ) {
                if ( line.indexOf(':') == -1 ) {
                    result.add(new File(line).getAbsolutePath());
                } else {
                    result.add(line);
                }
            }
        }
        return result;
    }

    private static void processFile(final List<String> paths, final File f)
    throws IOException {
        if ( f.getName().endsWith(".ref") ) {
            paths.addAll(parseFeatureRefFile(f));
        } else {
            paths.add(f.getAbsolutePath());
        }
    }

    /**
     * Get the list of feature files.
     * If the provided list of files is {@code null} or an empty array, the default is used.
     * The default checks for the following places, the first one found is used. If none is
     * found an empty list is returned.
     * <ol>
     *   <li>A directory named {@code feature} in the current directory
     *   <li>A file named {@code features.json} in the current directory
     *   <li>A directory named {@code feature} in the home directory
     *   <li>A file named {@code features.json} in the home directory
     * </ol>
     *
     * The list of files is processed one after the other. If it is relative, it is
     * first tried to be resolved against the current directory and then against the
     * home directory.
     * If an entry denotes a directory, all children ending in {@code .json} or {@code .ref} of that directory are read.
     * If a file ends in {@code .ref} the contents is read and every line not starting with the
     * hash sign is considered a reference to a feature artifact.
     *
     * @param homeDirectory If relative files should be resolved, this is the directory to use
     * @param files Optional list of files. If none is provided, a default is used.
     * @return The list of files.
     * @throws IOException If an error occurs.
     */
    public static List<String> getFeatureFiles(final File homeDirectory, final String... files) throws IOException {
        String[] featureFiles = files;
        if ( featureFiles == null || featureFiles.length == 0 ) {
            // Default value - check feature directory otherwise features file
            final File[] candidates = new File[] {
                    new File(homeDirectory, "features"),
                    new File(homeDirectory, "features.json"),
                    new File("features"),
                    new File("features.json")
            };
            File f = null;
            for(final File c : candidates) {
                if ( c.exists() ) {
                    f = c;
                    break;
                }
            }
            // nothing found, we default to the first candidate and fail later
            if ( f == null ) {
                f = candidates[0];
            }

            featureFiles = new String[] {f.getAbsolutePath()};
        }

        final List<String> paths = new ArrayList<>();
        for(final String name : featureFiles) {
            // check for absolute
            if ( name.indexOf(':') > 1 ) {
                paths.add(name);
            } else {
                // file or relative
                File f = null;
                final File test = new File(name);
                if ( test.isAbsolute() ) {
                    f = test;
                } else {
                    final File[] candidates = {
                            new File(homeDirectory, name),
                            new File(homeDirectory, "features" + File.separatorChar + name),
                            new File(name),
                            new File("features" + File.separatorChar + name),
                    };
                    for(final File c : candidates) {
                        if ( c.exists() && c.isFile() ) {
                            f = c;
                            break;
                        }
                    }
                }

                if ( f != null && f.exists() ) {
                    if ( f.isFile() ) {
                        processFile(paths, f);
                    } else {
                        processDir(paths, f);
                    }
                } else {
                    // we simply add the path and fail later on
                    paths.add(new File(name).getAbsolutePath());
                }
            }
        }

        Collections.sort(paths, FEATURE_PATH_COMP);
        return paths;
    }

    /**
     * Assemble an application based on the given files.
     *
     * Read the features and assemble the application
     * @param app The optional application to use as a base.
     * @param featureFiles The feature files.
     * @param artifactManager The artifact manager
     * @return The assembled application
     * @throws IOException If a feature can't be read or no feature is found.
     * @see #getFeatureFiles(File, String...)
     */
    public static Application assembleApplication(
            Application app,
            final ArtifactManager artifactManager, final String... featureFiles)
    throws IOException {
        final List<Feature> features = new ArrayList<>();
        for(final String initFile : featureFiles) {
            final Feature f = getFeature(initFile, artifactManager);
            features.add(f);
        }

        return assembleApplication(app, artifactManager, orderFeatures(artifactManager, features.toArray(new Feature[0])));
    }

    private static Feature[] orderFeatures(ArtifactManager artifactManager, Feature[] features) throws IOException {
        Resource mainBundle = null;

        Map<Resource, Feature> bundleMap = new HashMap<>();
        for (Feature f : features) {
            for (Artifact b : f.getBundles()) {
                BundleDescriptor bd = getBundleDescriptor(artifactManager, b);
                Resource r = new ResourceImpl(bd);
                bundleMap.put(r, f);

                // TODO where do we start? Anywhere?
                if (bd.getBundleSymbolicName().equals("recording-servlet"))
                    mainBundle = r;
            }
        }

        // Add these to the available features
        Artifact lpa = new Artifact(ArtifactId.parse("org.apache.sling/org.apache.sling.launchpad.api/1.2.0"));
        bundleMap.put(new ResourceImpl(getBundleDescriptor(artifactManager, lpa)), null); // What feature?
        bundleMap.put(frameworkResource, null);

        try {
            Map<Resource, List<Wire>> deps = resolver.resolve(new ResolveContextImpl(mainBundle, bundleMap.keySet()));
            System.out.println("#### " + deps);

            List<Resource> orderedBundles = new LinkedList<>();
            for (Map.Entry<Resource, List<Wire>> entry : deps.entrySet()) {
                Resource curBundle = entry.getKey();
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
            return orderedFeatures.toArray(new Feature[] {});
        } catch (ResolutionException e) {
            throw new RuntimeException(e);
        }
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

    public static Application assembleApplication(
            Application app,
            final ArtifactManager artifactManager, final Feature... features)
    throws IOException {
        if ( features.length == 0 ) {
            throw new IOException("No features found.");
        }

        app = ApplicationBuilder.assemble(app, new BuilderContext(new FeatureProvider() {

            @Override
            public Feature provide(final ArtifactId id) {
                try {
                    final ArtifactHandler handler = artifactManager.getArtifactHandler("mvn:" + id.toMvnPath());
                    try (final FileReader r = new FileReader(handler.getFile())) {
                        final Feature f = FeatureJSONReader.read(r, handler.getUrl());
                        return f;
                    }

                } catch (final IOException e) {
                    // ignore
                }
                return null;
            }
        }), features);

        // check framework
        if ( app.getFramework() == null ) {
            // use hard coded Apache Felix
            app.setFramework(getFelixFrameworkId(null));
        }

        return app;
    }

    /**
     * Read the feature
     *
     * @param file The feature file
     * @param artifactManager The artifact manager to read the feature
     * @return The read feature
     * @throws IOException If reading fails
     */
    private static Feature getFeature(final String file,
            final ArtifactManager artifactManager)
    throws IOException {
        final ArtifactHandler featureArtifact = artifactManager.getArtifactHandler(file);

        try (final FileReader r = new FileReader(featureArtifact.getFile())) {
            final Feature f = FeatureJSONReader.read(r, featureArtifact.getUrl());
            return f;
        }
    }

    private static Resolver loadResolver() {
        // Launch an OSGi framework and obtain its resolver
        try {
            for (FrameworkFactory fwf : ServiceLoader.load(FrameworkFactory.class)) {
                Framework fw = fwf.newFramework(Collections.emptyMap());
                fw.init();
                fw.start();
                BundleContext ctx = fw.getBundleContext();

                /* */
                BundleRevision br = fw.adapt(BundleRevision.class);
                List<Capability> caps = br.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
//                System.out.println("~~~" + caps);
                frameworkResource = new ResourceImpl("framework", Collections.singletonMap(PackageNamespace.PACKAGE_NAMESPACE, caps), Collections.emptyMap());
                /* */

                int i=0;
                while (i < 20) {
                    ServiceReference<Resolver> ref = ctx.getServiceReference(Resolver.class);
                    if (ref != null)
                        return ctx.getService(ref);

                    // The service isn't there yet, let's wait a little and try again
                    Thread.sleep(500);
                    i++;
                }
            }
        } catch (BundleException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("OSGi resolver cannot be obtained");
    }
}
