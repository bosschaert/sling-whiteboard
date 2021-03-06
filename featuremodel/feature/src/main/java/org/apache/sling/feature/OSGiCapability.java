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
package org.apache.sling.feature;

import java.util.Map;

import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

/**
 * Implementation of the OSGi Capability interface.
 */
public class OSGiCapability extends AbstractCapabilityRequirement implements Capability {
    /**
     * Create a capability that is not associated with a resource.
     * @param res The resource associated with the capability. May be null.
     * @param ns The namespace of the capability.
     * @param attrs The attributes of the capability.
     * @param dirs The directives of the capability.
     */
    public OSGiCapability(String ns, Map<String, Object> attrs, Map<String, String> dirs) {
        this(null, ns, attrs, dirs);
    }

    /**
     * Create a capability.
     * @param res The resource associated with the capability. May be null.
     * @param ns The namespace of the capability.
     * @param attrs The attributes of the capability.
     * @param dirs The directives of the capability.
     */
    public OSGiCapability(Resource res, String ns, Map<String, Object> attrs, Map<String, String> dirs) {
        super(res, ns, attrs, dirs);
    }

    /**
     * Create a capability based on an existing capability, providing the resource.
     * The namespace, attributes and directives are copied from the provided capability.
     * @param resource The resource to be associated with the capability
     * @param capability The capability to base the new requirement on.
     */
    public OSGiCapability(Resource resource, Capability capability) {
        this(resource, capability.getNamespace(), capability.getAttributes(), capability.getDirectives());
    }
}
