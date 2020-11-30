/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.experimental.typesystem;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The Sling Type System augments a {@link org.apache.sling.api.resource.ResourceResolver#PROPERTY_RESOURCE_TYPE} value, by providing more
 * context about how a {@link org.apache.sling.api.resource.Resource} of that type should look like in terms of properties, their values and
 * their presence (mandatory or not).
 */
@ProviderType
public interface Type {

    /**
     * Provides the {@link org.apache.sling.api.resource.ResourceResolver#PROPERTY_RESOURCE_TYPE} value that {@code this} type augments.
     *
     * @return the resource type value
     */
    @NotNull
    String getResourceType();

    /**
     * If the augmented resource type inherits from another resource type, this method will return the value of the {@code
     * sling:resourceSuperType} resource property. In addition to that, the {@link #getProperties()} method will return a merged view of the
     * available properties.
     *
     * @return the resource super type value, if present; {@code null} otherwise
     * @see #getResourceType()
     * @see #getProperties()
     */
    @Nullable
    String getResourceSuperType();


    @NotNull
    Set<Property> getProperties();

}