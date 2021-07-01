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
package org.apache.sling.feature.osgi.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.apache.felix.cm.json.impl.TypeConverter;
import org.osgi.service.feature.BuilderFactory;
import org.osgi.service.feature.Feature;
import org.osgi.service.feature.FeatureBuilder;
import org.osgi.service.feature.FeatureBundle;
import org.osgi.service.feature.FeatureBundleBuilder;
import org.osgi.service.feature.FeatureConfiguration;
import org.osgi.service.feature.FeatureConfigurationBuilder;
import org.osgi.service.feature.FeatureExtension;
import org.osgi.service.feature.FeatureExtensionBuilder;
import org.osgi.service.feature.FeatureService;
import org.osgi.service.feature.ID;

public class FeatureServiceImpl implements FeatureService {
    private final BuilderFactoryImpl builderFactory = new BuilderFactoryImpl();

    public BuilderFactory getBuilderFactory() {
        return builderFactory;
    }
    
    @Override
	public ID getIDfromMavenCoordinates(String mavenID) {
    	return IDImpl.fromMavenID(mavenID);
	}

	@Override
	public ID getID(String groupId, String artifactId, String version) {
		return new IDImpl(groupId, artifactId, version, null, null);
	}

	@Override
	public ID getID(String groupId, String artifactId, String version, String type) {
		return new IDImpl(groupId, artifactId, version, type, null);
	}

	@Override
	public ID getID(String groupId, String artifactId, String version, String type, String classifier) {
		return new IDImpl(groupId, artifactId, version, type, classifier);
	}

	public Feature readFeature(Reader jsonReader) throws IOException {
        JsonObject json = Json.createReader(jsonReader).readObject();

        String id = json.getString("id");
        FeatureBuilder builder = builderFactory.newFeatureBuilder(getIDfromMavenCoordinates(id));

        builder.setName(json.getString("name", null));
        builder.setDescription(json.getString("description", null));
        builder.setDocURL(json.getString("docURL", null));
        builder.setLicense(json.getString("license", null));
        builder.setSCM(json.getString("scm", null));
        builder.setVendor(json.getString("vendor", null));

        builder.setComplete(json.getBoolean("complete", false));

        builder.addBundles(getBundles(json));
        builder.addCategories(getCategories(json));
        builder.addConfigurations(getConfigurations(json));
        builder.addExtensions(getExtensions(json));

        return builder.build();
    }

    private FeatureBundle[] getBundles(JsonObject json) {
        JsonArray ja = json.getJsonArray("bundles");
        if (ja == null)
            return new FeatureBundle[] {};

        List<FeatureBundle> bundles = new ArrayList<>();

        for (JsonValue val : ja) {
            if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject jo = val.asJsonObject();
                String bid = jo.getString("id");
                FeatureBundleBuilder builder = builderFactory.newBundleBuilder(getIDfromMavenCoordinates(bid));

                for (Map.Entry<String, JsonValue> entry : jo.entrySet()) {
                    if (entry.getKey().equals("id"))
                        continue;

                    JsonValue value = entry.getValue();

                    Object v;
                    switch (value.getValueType()) {
                    case NUMBER:
                        v = ((JsonNumber) value).longValueExact();
                        break;
                    case STRING:
                        v = ((JsonString) value).getString();
                        break;
                    default:
                        v = value.toString();
                    }
                    builder.addMetadata(entry.getKey(), v);
                }
                bundles.add(builder.build());
            }
        }

        return bundles.toArray(new FeatureBundle[0]);
    }

    private String[] getCategories(JsonObject json) {
        JsonArray ja = json.getJsonArray("categories");
        if (ja == null)
            return new String[] {};

        List<String> cats = ja.getValuesAs(JsonString::getString);
        return cats.toArray(new String[] {});
    }

    private FeatureConfiguration[] getConfigurations(JsonObject json) {
        JsonObject jo = json.getJsonObject("configurations");
        if (jo == null)
            return new FeatureConfiguration[] {};

        List<FeatureConfiguration> configs = new ArrayList<>();

        for (Map.Entry<String, JsonValue> entry : jo.entrySet()) {

            String p = entry.getKey();
            String factoryPid = null;
            int idx = p.indexOf('~');
            if (idx > 0) {
                factoryPid = p.substring(0, idx);
                p = p.substring(idx + 1);
            }

            FeatureConfigurationBuilder builder;
            if (factoryPid == null) {
                builder = builderFactory.newConfigurationBuilder(p);
            } else {
                builder = builderFactory.newConfigurationBuilder(factoryPid, p);
            }

            JsonObject values = entry.getValue().asJsonObject();
            for (Map.Entry<String, JsonValue> value : values.entrySet()) {
            	String key = value.getKey();
            	String typeInfo = null;
            	int cidx = key.indexOf(':');
            	if (cidx > 0) {
            		typeInfo = key.substring(cidx + 1);
            		key = key.substring(0, cidx);
            	}
            	
                JsonValue val = value.getValue();
                Object v = TypeConverter.convertObjectToType(val, typeInfo);                
                builder.addValue(key, v);
            }
            configs.add(builder.build());
        }

        return configs.toArray(new FeatureConfiguration[] {});
    }

    private FeatureExtension[] getExtensions(JsonObject json) {
        JsonObject jo = json.getJsonObject("extensions");
        if (jo == null)
            return new FeatureExtension[] {};

        List<FeatureExtension> extensions = new ArrayList<>();

        for (Map.Entry<String,JsonValue> entry : jo.entrySet()) {
            JsonObject exData = entry.getValue().asJsonObject();
            FeatureExtension.Type type;
            if (exData.containsKey("text")) {
                type = FeatureExtension.Type.TEXT;
            } else if (exData.containsKey("artifacts")) {
                type = FeatureExtension.Type.ARTIFACTS;
            } else if (exData.containsKey("json")) {
                type = FeatureExtension.Type.JSON;
            } else {
                throw new IllegalStateException("Invalid extension: " + entry);
            }
            String k = exData.getString("kind", "optional");
            FeatureExtension.Kind kind = FeatureExtension.Kind.valueOf(k.toUpperCase());

            FeatureExtensionBuilder builder = builderFactory.newExtensionBuilder(entry.getKey(), type, kind);

            switch (type) {
            case TEXT:
                builder.addText(exData.getString("text"));
                break;
            case ARTIFACTS:
                JsonArray ja2 = exData.getJsonArray("artifacts");
                for (JsonValue jv : ja2) {
                    if (jv.getValueType() == JsonValue.ValueType.STRING) {
                        String id = ((JsonString) jv).getString();
                        builder.addArtifact(getIDfromMavenCoordinates(id));
                    }
                }
                break;
            case JSON:
                builder.setJSON(exData.getJsonObject("json").toString());
                break;
            }
            extensions.add(builder.build());
        }

        return extensions.toArray(new FeatureExtension[] {});
    }

    public void writeFeature(Feature feature, Writer jsonWriter) throws IOException {
    	// LinkedHashMap to give it some order, we'd like 'id' and 'name' first.
    	Map<String,Object> attrs = new LinkedHashMap<>();
    	
    	attrs.put("id", feature.getID().toString());
    	feature.getName().ifPresent(n -> attrs.put("name", n));
    	feature.getDescription().ifPresent(d -> attrs.put("description", d));
    	feature.getDocURL().ifPresent(d -> attrs.put("docURL", d));
    	feature.getLicense().ifPresent(l -> attrs.put("license", l));
    	feature.getSCM().ifPresent(s -> attrs.put("scm", s));
    	feature.getVendor().ifPresent(v -> attrs.put("vendor", v));
    	
		JsonObjectBuilder json = Json.createObjectBuilder(attrs);
		JsonObject fo = json.build();
		
		JsonGeneratorFactory gf = Json.createGeneratorFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
		try (JsonGenerator gr = gf.createGenerator(jsonWriter)) {
			gr.write(fo);
		}
    }
}
