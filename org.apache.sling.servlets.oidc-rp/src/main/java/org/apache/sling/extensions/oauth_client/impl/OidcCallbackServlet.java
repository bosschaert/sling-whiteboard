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
package org.apache.sling.extensions.oauth_client.impl;

import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.auth.core.AuthConstants;
import org.apache.sling.extensions.oauth_client.ClientConnection;
import org.apache.sling.extensions.oauth_client.OAuthTokenStore;
import org.apache.sling.extensions.oauth_client.OAuthTokens;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationResponse;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;

@Component(service = { Servlet.class },
    property = { AuthConstants.AUTH_REQUIREMENTS +"=" + OidcCallbackServlet.PATH }
)
@SlingServletPaths(OidcCallbackServlet.PATH)
public class OidcCallbackServlet extends SlingAllMethodsServlet {

    static final String PATH = "/system/sling/oidc/callback";

    private static final long serialVersionUID = 1L;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<String, ClientConnection> connections;
    private final OAuthTokenStore tokenStore;

    static String getCallbackUri(HttpServletRequest request) {
        String portFragment = "";
        boolean isNonDefaultHttpPort = request.getScheme().equals("http") && request.getServerPort() != 80;
        boolean isNonDefaultHttpsPort = request.getScheme().equals("https") && request.getServerPort() != 443;
        if ( isNonDefaultHttpPort || isNonDefaultHttpsPort )
            portFragment = ":" + request.getServerPort();

        return request.getScheme() + "://" + request.getServerName() + portFragment + PATH;
    }

    @Activate
    public OidcCallbackServlet(@Reference(policyOption = GREEDY) List<ClientConnection> connections, @Reference OAuthTokenStore tokenStore) {
        this.connections = connections.stream()
                .collect(Collectors.toMap( ClientConnection::name, Function.identity()));
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        try {
            StringBuffer requestURL = request.getRequestURL();
            if ( request.getQueryString() != null )
                requestURL.append('?').append(request.getQueryString());

            AuthorizationResponse authResponse = AuthorizationResponse.parse(new URI(requestURL.toString()));
            
            OAuthStateManager stateManager = OAuthStateManager.stateFor(request);
            if ( authResponse.getState() == null || !stateManager.isValidState(authResponse.getState()))
                throw new ServletException("Failed state check");

            if ( !authResponse.indicatesSuccess() )
                throw new ServletException(authResponse.toErrorResponse().getErrorObject().toString());

            Optional<String> redirect = stateManager.getStateAttribute(authResponse.getState(), OAuthStateManager.PARAMETER_NAME_REDIRECT);
            
            String authCode = authResponse.toSuccessResponse().getAuthorizationCode().getValue();
            
            Optional<String> desiredConnectionName = stateManager.getStateAttribute(authResponse.getState(), OAuthStateManager.PARAMETER_NAME_CONNECTION);
            stateManager.unregisterState(authResponse.getState());
            if ( desiredConnectionName.isEmpty() ) {
                logger.warn("Did not find any connection in stateManager");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);  
                return;
            }
            ClientConnection connection = connections.get(desiredConnectionName.get());
            if ( connection == null ) {
                logger.warn("Requested unknown connection {}", desiredConnectionName.get());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            ResolvedOAuthConnection conn = ResolvedOAuthConnection.resolve(connection);

            ClientID clientId = new ClientID(conn.clientId());
            Secret clientSecret = new Secret(conn.clientSecret());
            ClientSecretBasic clientCredentials = new ClientSecretBasic(clientId, clientSecret);
            
            AuthorizationCode code = new AuthorizationCode(authCode);
            
            URI tokenEndpoint = new URI(conn.tokenEndpoint());
            
            TokenRequest tokenRequest = new TokenRequest(
                tokenEndpoint,
                clientCredentials,
                new AuthorizationCodeGrant(code, new URI(getCallbackUri(request)))
            );
            
            HTTPRequest httpRequest = tokenRequest.toHTTPRequest();
            // GitHub requires an explicitly set Accept header, otherwise the response is url encoded
            // https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#2-users-are-redirected-back-to-your-site-by-github
            // see also https://bitbucket.org/connect2id/oauth-2.0-sdk-with-openid-connect-extensions/issues/107/support-application-x-www-form-urlencoded
            httpRequest.setHeader("Accept", "application/json"); 
            HTTPResponse httpResponse = httpRequest.send();
            
            TokenResponse tokenResponse = TokenResponse.parse(httpResponse);
            
            if ( !tokenResponse.indicatesSuccess() ) {
                TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
                logger.warn("Error returned when trying to obtain access token : {}, {}", 
                        errorResponse.getErrorObject().getCode(), errorResponse.getErrorObject().getDescription());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            OAuthTokens tokens = Converter.toSlingOAuthTokens(tokenResponse.toSuccessResponse().getTokens());
            
            tokenStore.persistTokens(connection, request.getResourceResolver(), tokens);

            if ( redirect.isEmpty() ) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.addHeader("Content-Type", "text/plain");
                response.getWriter().write("OK");
                response.getWriter().flush();
                response.getWriter().close();
            } else {
                response.sendRedirect(URLDecoder.decode(redirect.get(), StandardCharsets.UTF_8));
            }
        } catch (ParseException | URISyntaxException | IOException e) {
            throw new ServletException(e);
        }
    }

}
