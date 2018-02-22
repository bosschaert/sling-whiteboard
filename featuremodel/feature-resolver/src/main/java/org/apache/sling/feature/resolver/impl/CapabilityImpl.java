package org.apache.sling.feature.resolver.impl;

import java.util.Map;

import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

public class CapabilityImpl extends AbstractCapabilityRequirement implements Capability {
    public CapabilityImpl(Resource res, String ns, Map<String, Object> attrs, Map<String, String> dirs) {
        super(res, ns, attrs, dirs);
    }
}
