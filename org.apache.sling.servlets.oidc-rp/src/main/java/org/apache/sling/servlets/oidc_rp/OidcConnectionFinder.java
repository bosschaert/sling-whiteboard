/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.servlets.oidc_rp;

import org.apache.sling.api.resource.ResourceResolver;

// TODO - bad name
// thoughts on the API
// we have two main parts 
// I. repository-based storage for tokens ( get access token / refresh token / user token )
// II. interactions with the authorization and token endpoints ( build redirect URL for auth, use refresh token to get new access/refresh tokens , exchanging access codes for tokens )
// These should be split into two client-facing interfaces, and the code from the Servlets pushed into II.
// It is definitely an unpleasant smell that the OidcConnnectionFinderImpl needs to reach out to the OidcClient.
// 
// Further question - do we need a way of introspecting the OidcConnections?
public interface OidcConnectionFinder {

    OidcToken getAccessToken(OidcConnection connection, ResourceResolver resolver);
    
    /* TODO OidcToken getRefreshToken(OidcConnection connection, ResourceResolver resolver); */
}
