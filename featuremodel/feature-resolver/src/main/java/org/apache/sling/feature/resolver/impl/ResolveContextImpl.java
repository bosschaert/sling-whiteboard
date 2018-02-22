package org.apache.sling.feature.resolver.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;

public class ResolveContextImpl extends ResolveContext {
    private final Resource bundle;
    private final Collection<Resource> availableResources;

    public ResolveContextImpl(Resource mainResource, Collection<Resource> available) {
        bundle = mainResource;
        availableResources = available;
    }

    @Override
    public Collection<Resource> getMandatoryResources() {
        return Collections.singleton(bundle);
    }

    @Override
    public List<Capability> findProviders(Requirement requirement) {
        List<Capability> providers = new ArrayList<>();

        String f = requirement.getDirectives().get("filter");
        try {
            Filter filter = FrameworkUtil.createFilter(f);
            for (Resource r : availableResources) {
                for (Capability c : r.getCapabilities(requirement.getNamespace())) {
                    if (filter.matches(c.getAttributes())) {
                        providers.add(c);
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException("Invalid filter " + f + " in requirement " + requirement);
        }

        return providers;
    }

    @Override
    public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
        capabilities.add(0, hostedCapability);
        return 0;
    }

    @Override
    public boolean isEffective(Requirement requirement) {
        String eff = requirement.getDirectives().get("effective");
        if (eff == null)
            return true; // resolve is the default
        return "resolve".equals(eff.trim());
    }

    @Override
    public Map<Resource, Wiring> getWirings() {
        return Collections.emptyMap();
    }
}
