package org.apache.sling.feature.resolver.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.osgi.resource.Resource;

public abstract class AbstractCapabilityRequirement {
    private final Resource resource;
    private final String namespace;
    private final Map<String, Object> attributes;
    private final Map<String, String> directives;

    public AbstractCapabilityRequirement(Resource res, String ns, Map<String, Object> attrs, Map<String, String> dirs) {
        resource = res;
        namespace = ns;
        attributes = Collections.unmodifiableMap(new HashMap<>(attrs));
        directives = Collections.unmodifiableMap(new HashMap<>(dirs));
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Map<String, String> getDirectives() {
        return directives;
    }

    public Resource getResource() {
        return resource;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attributes == null) ? 0 : attributes.hashCode());
        result = prime * result + ((directives == null) ? 0 : directives.hashCode());
        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractCapabilityRequirement other = (AbstractCapabilityRequirement) obj;
        if (attributes == null) {
            if (other.attributes != null)
                return false;
        } else if (!attributes.equals(other.attributes))
            return false;
        if (directives == null) {
            if (other.directives != null)
                return false;
        } else if (!directives.equals(other.directives))
            return false;
        if (namespace == null) {
            if (other.namespace != null)
                return false;
        } else if (!namespace.equals(other.namespace))
            return false;
        /* Does this cause an endless loop? TODO
        if (resource == null) {
            if (other.resource != null)
                return false;
        } else if (!resource.equals(other.resource))
            return false;
        */
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [resource=" + resource + ", namespace=" + namespace + ", attributes=" + attributes
                + ", directives=" + directives + "]";
    }
}
