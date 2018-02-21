package org.apache.sling.feature.support.resolver;

import java.util.Map;

import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class RequirementImpl extends AbstractCapabilityRequirement implements Requirement {
    public RequirementImpl(Resource res, String ns, Map<String, Object> attrs, Map<String, String> dirs) {
        super(res, ns, attrs, dirs);
    }
}
