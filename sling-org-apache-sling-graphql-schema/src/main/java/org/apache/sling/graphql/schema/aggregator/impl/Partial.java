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
package org.apache.sling.graphql.schema.aggregator.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;

/** Wrapper for the partials format, that parses a partial file and
 *  provides access to its sections.
 *  See the example.partial.txt and the tests for a description of
 *  the format.
  */
interface Partial {
    /** A section in the partial */
    interface Section {
        String getName();
        String getDescription();
        Reader getContent() throws IOException;
    }

    /** The name of this partial */
    String getName();

    /** Return a specific section of the partial, by name */
    Optional<Section> getSection(String name);
}