package org.apache.sling.feature.process;

import java.util.List;

import org.apache.sling.feature.Feature;

public interface FeatureResolver extends AutoCloseable {

    List<Feature> orderFeatures(List<? extends Feature> features);

}
