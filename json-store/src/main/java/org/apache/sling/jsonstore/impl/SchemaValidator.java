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

package org.apache.sling.jsonstore.impl;

import static org.apache.sling.jsonstore.api.JsonStoreConstants.SCHEMA_DATA_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jsonstore.api.JsonStoreValidator;
import org.osgi.service.component.annotations.Component;

@Component(service = JsonStoreValidator.class)
public class SchemaValidator implements JsonStoreValidator {

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);

    @Override
    public boolean validate(ResourceResolver resolver, JsonNode json, String site, String dataType) throws JsonStoreValidator.ValidatorException {
        if(!SCHEMA_DATA_TYPE.equals(dataType)) {
            return false;
        }
        // TODO how to verify that we have a valid schema?
        // https://github.com/networknt/json-schema-validator
        final JsonSchema schema = factory.getSchema(json);
        schema.initializeValidators();
        return true;
    }
}