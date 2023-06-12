/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazonaws.serverless.proxy.internal.testutils;

import com.amazonaws.serverless.proxy.internal.LambdaContainerHandler;
import com.amazonaws.serverless.proxy.model.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.ByteArrayBody;
import org.apache.hc.client5.http.entity.mime.StringBody;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * Request builder object. This is used by unit proxy to quickly create an AWS_PROXY request object
 */
public class AwsProxyRequestBuilder {

    //-------------------------------------------------------------
    // Variables - Private
    //-------------------------------------------------------------

    private APIGatewayProxyRequestEvent request;
    private MultipartEntityBuilder multipartBuilder;

    //-------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------

    public AwsProxyRequestBuilder() {
        this(null, null);
    }


    public AwsProxyRequestBuilder(String path) {
        this(path, null);
    }

    public AwsProxyRequestBuilder(APIGatewayProxyRequestEvent req) {
        request = req;
    }


    public AwsProxyRequestBuilder(String path, String httpMethod) {
        this.request = new APIGatewayProxyRequestEvent();
        this.request.setMultiValueHeaders(new Headers()); // avoid NPE
        this.request.setHttpMethod(httpMethod);
        this.request.setPath(path);
        this.request.setMultiValueQueryStringParameters(new MultiValuedTreeMap<>());
        this.request.setRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());
        this.request.getRequestContext().setRequestId(UUID.randomUUID().toString());
        this.request.getRequestContext().setExtendedRequestId(UUID.randomUUID().toString());
        this.request.getRequestContext().setStage("test");
        this.request.getRequestContext().setProtocol("HTTP/1.1");
        this.request.getRequestContext().setRequestTimeEpoch(System.currentTimeMillis());
        APIGatewayProxyRequestEvent.RequestIdentity identity = new APIGatewayProxyRequestEvent.RequestIdentity();
        identity.setSourceIp("127.0.0.1");
        this.request.getRequestContext().setIdentity(identity);
        this.request.setIsBase64Encoded(false);
    }


    //-------------------------------------------------------------
    // Methods - Public
    //-------------------------------------------------------------

    public ApplicationLoadBalancerRequestEvent toAlbRequest() {
        ApplicationLoadBalancerRequestEvent req = new ApplicationLoadBalancerRequestEvent();

        ApplicationLoadBalancerRequestEvent.Elb elb = new ApplicationLoadBalancerRequestEvent.Elb();
        elb.setTargetGroupArn("arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/lambda-target/d6190d154bc908a5");

        ApplicationLoadBalancerRequestEvent.RequestContext requestContext = new ApplicationLoadBalancerRequestEvent.RequestContext();
        requestContext.setElb(elb);

        req.setRequestContext(requestContext);
        req.setHttpMethod(request.getHttpMethod());
        req.setPath(request.getPath());
        req.setQueryStringParameters(request.getQueryStringParameters());
        req.setMultiValueQueryStringParameters(request.getMultiValueQueryStringParameters());
        req.setHeaders(request.getHeaders());
        req.setMultiValueHeaders(request.getMultiValueHeaders());
        req.setBody(request.getBody());
        req.setIsBase64Encoded(request.getIsBase64Encoded());

        return req;
    }

    public AwsProxyRequestBuilder stage(String stageName) {
        this.request.getRequestContext().setStage(stageName);
        return this;
    }

    public AwsProxyRequestBuilder method(String httpMethod) {
        this.request.setHttpMethod(httpMethod);
        return this;
    }


    public AwsProxyRequestBuilder path(String path) {
        this.request.setPath(path);
        return this;
    }


    public AwsProxyRequestBuilder json() {
        return this.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    }


    public AwsProxyRequestBuilder form(String key, String value) {
        if (request.getMultiValueHeaders() == null) {
            request.setMultiValueHeaders(new Headers());
        }
        if (request.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE) == null) {
            request.getMultiValueHeaders().put(HttpHeaders.CONTENT_TYPE, new ArrayList<>());
        }
        request.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).add(MediaType.APPLICATION_FORM_URLENCODED);  // TODO: Potentially reate CONTENT_TYPE list first
        String body = request.getBody();
        if (body == null) {
            body = "";
        }
        body += (body.equals("")?"":"&") + key + "=" + value;
        request.setBody(body);
        return this;
    }

    public AwsProxyRequestBuilder formFilePart(String fieldName, String fileName, byte[] content) throws IOException {
        if (multipartBuilder == null) {
            multipartBuilder = MultipartEntityBuilder.create();
        }
        multipartBuilder.addPart(fieldName, new ByteArrayBody(content, fileName));
        buildMultipartBody();
        return this;
    }

    public AwsProxyRequestBuilder formTextFieldPart(String fieldName, String fieldValue)
            throws IOException {
        if (request.getMultiValueHeaders() == null) {
            request.setMultiValueHeaders(new Headers());
        }
        if (multipartBuilder == null) {
            multipartBuilder = MultipartEntityBuilder.create();
        }
        multipartBuilder.addPart(fieldName, new StringBody(fieldValue, ContentType.TEXT_PLAIN));
        buildMultipartBody();
        return this;
    }

    private void buildMultipartBody()
            throws IOException {
        HttpEntity bodyEntity = multipartBuilder.build();
        InputStream bodyStream = bodyEntity.getContent();
        byte[] buffer = new byte[bodyStream.available()];
        IOUtils.readFully(bodyStream, buffer);
        byte[] finalBuffer = new byte[buffer.length + 1];
        byte[] newLineBytes = "\n\n".getBytes(LambdaContainerHandler.getContainerConfig().getDefaultContentCharset());
        System.arraycopy(newLineBytes, 0, finalBuffer, 0, newLineBytes.length);
        System.arraycopy(buffer, 0, finalBuffer, newLineBytes.length - 1, buffer.length);
        request.setBody(Base64.getMimeEncoder().encodeToString(finalBuffer));
        request.setIsBase64Encoded(true);
        this.request.setMultiValueHeaders(new Headers());
        header(HttpHeaders.CONTENT_TYPE, bodyEntity.getContentType());
        header(HttpHeaders.CONTENT_LENGTH, bodyEntity.getContentLength() + "");
    }


    public AwsProxyRequestBuilder header(String key, String value) {
        if (this.request.getMultiValueHeaders() == null) {
            this.request.setMultiValueHeaders(new Headers());
        }

        if (this.request.getMultiValueHeaders().get(key) != null) {
            this.request.getMultiValueHeaders().get(key).add(value);
        } else {
            List<String> values = new ArrayList<>();
            values.add(value);
            this.request.getMultiValueHeaders().put(key, values);
        }

        return this;
    }

    public AwsProxyRequestBuilder multiValueHeaders(Headers h) {
        this.request.setMultiValueHeaders(h);
        return this;
    }

    public AwsProxyRequestBuilder multiValueQueryString(MultiValuedTreeMap<String, String> params) {
        this.request.setMultiValueQueryStringParameters(params);
        return this;
    }


    public AwsProxyRequestBuilder queryString(String key, String value) {
        if (this.request.getMultiValueQueryStringParameters() == null) {
            this.request.setMultiValueQueryStringParameters(new HashMap<String, List<String>>());
        }

        List<String> values = this.request.getMultiValueQueryStringParameters().get(key);
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(value);
        this.request.getMultiValueQueryStringParameters().put(key, values);

        return this;
    }

    public AwsProxyRequestBuilder body(String body) {
        this.request.setBody(body);
        return this;
    }

    public AwsProxyRequestBuilder nullBody() {
        this.request.setBody(null);
        return this;
    }

    public AwsProxyRequestBuilder body(Object body) {
        if (request.getMultiValueHeaders() != null && request.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0).startsWith(MediaType.APPLICATION_JSON)) {
            try {
                return body(LambdaContainerHandler.getObjectMapper().writeValueAsString(body));
            } catch (JsonProcessingException e) {
                throw new UnsupportedOperationException("Could not serialize object: " + e.getMessage());
            }
        } else {
            throw new UnsupportedOperationException("Unsupported content type in request");
        }
    }

    public AwsProxyRequestBuilder apiId(String id) {
        if (request.getRequestContext() == null) {
            request.setRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());
        }
        request.getRequestContext().setApiId(id);
        return this;
    }

    public AwsProxyRequestBuilder binaryBody(InputStream is)
            throws IOException {
        this.request.setIsBase64Encoded(true);
        return body(Base64.getMimeEncoder().encodeToString(IOUtils.toByteArray(is)));
    }


    public AwsProxyRequestBuilder authorizerPrincipal(String principal) {
        if (this.request.getRequestContext().getAuthorizer() == null) {
            this.request.getRequestContext().setAuthorizer(new HashMap<String, Object>());
        }
        this.request.getRequestContext().getAuthorizer().put("principalId", principal);
        this.request.getRequestContext().getAuthorizer().computeIfAbsent("claims", k -> new HashMap<String, String>());
        ((Map<String, String>) this.request.getRequestContext().getAuthorizer().get("claims")).put("sub", principal);

        return this;
    }

    public AwsProxyRequestBuilder authorizerContextValue(String key, String value) {
        if (this.request.getRequestContext().getAuthorizer() == null) {
            this.request.getRequestContext().setAuthorizer(new HashMap<>());
        }
        this.request.getRequestContext().getAuthorizer().put(key, value);
        return this;
    }


    public AwsProxyRequestBuilder cognitoUserPool(String identityId) {
        this.request.getRequestContext().getIdentity().setCognitoAuthenticationType("POOL");
        this.request.getRequestContext().getIdentity().setCognitoIdentityId(identityId);
        if (this.request.getRequestContext().getAuthorizer() == null) {
            this.request.getRequestContext().setAuthorizer(new HashMap<>());
        }
        this.request.getRequestContext().getAuthorizer().put("claims", new HashMap<>());
        ((Map<String, String>) this.request.getRequestContext().getAuthorizer().get("claims")).put("sub", identityId);

        return this;
    }

    public AwsProxyRequestBuilder claim(String claim, String value) {
        ((Map<String, String>) this.request.getRequestContext().getAuthorizer().get("claims")).put(claim, value);
        return this;
    }


    public AwsProxyRequestBuilder cognitoIdentity(String identityId, String identityPoolId) {
        this.request.getRequestContext().getIdentity().setCognitoAuthenticationType("IDENTITY");
        this.request.getRequestContext().getIdentity().setCognitoIdentityId(identityId);
        this.request.getRequestContext().getIdentity().setCognitoIdentityPoolId(identityPoolId);
        return this;
    }


    public AwsProxyRequestBuilder cookie(String name, String value) {
        if (request.getMultiValueHeaders() == null) {
            request.setMultiValueHeaders(new Headers());
        }

        String cookies = getFirst(request.getMultiValueHeaders(), HttpHeaders.COOKIE);
        if (cookies == null) {
            cookies = "";
        }

        cookies += (cookies.equals("")?"":"; ") + name + "=" + value;
        putSingle(request.getMultiValueHeaders(), HttpHeaders.COOKIE, cookies);
        return this;
    }

    private String getFirst(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }

    private List<String> findKey(Map<String, List<String>> headers, String key) {
        List<String> values = headers.get(key);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(key, values);
        }
        return values;
    }

    public void putSingle(Map<String, List<String>> headers, String key, String value) {
        List<String> values = findKey(headers, key);
        values.clear();
        values.add(value);
    }


    public AwsProxyRequestBuilder scheme(String scheme) {
        if (request.getMultiValueHeaders() == null) {
            request.setMultiValueHeaders(new Headers());
        }

        putSingle(request.getMultiValueHeaders(),"CloudFront-Forwarded-Proto", scheme);

        return this;
    }

    public AwsProxyRequestBuilder serverName(String serverName) {
        if (request.getMultiValueHeaders() == null) {
            request.setMultiValueHeaders(new Headers());
        }
        putSingle(request.getMultiValueHeaders(), "Host", serverName);

        return this;
    }

    public AwsProxyRequestBuilder userAgent(String agent) {
        if (request.getRequestContext() == null) {
            request.setRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());
        }
        if (request.getRequestContext().getIdentity() == null) {
            request.getRequestContext().setIdentity(new APIGatewayProxyRequestEvent.RequestIdentity());
        }

        request.getRequestContext().getIdentity().setUserAgent(agent);
        return this;
    }

    public AwsProxyRequestBuilder referer(String referer) {
        if (request.getRequestContext() == null) {
            request.setRequestContext(new APIGatewayProxyRequestEvent.ProxyRequestContext());
        }
        if (request.getRequestContext().getIdentity() == null) {
            request.getRequestContext().setIdentity(new APIGatewayProxyRequestEvent.RequestIdentity());
        }

        request.getRequestContext().getIdentity().setCaller(referer);
        return this;
    }


    public AwsProxyRequestBuilder basicAuth(String username, String password) {
        // we remove the existing authorization strategy
        request.getMultiValueHeaders().remove(HttpHeaders.AUTHORIZATION);
        String authHeader = "Basic " + Base64.getMimeEncoder().encodeToString((username + ":" + password).getBytes(Charset.defaultCharset()));
        List<String> values = findKey(request.getMultiValueHeaders(), HttpHeaders.AUTHORIZATION);
        values.add(authHeader);

        return this;
    }

    public AwsProxyRequestBuilder fromJsonString(String jsonContent)
            throws IOException {
        request = LambdaContainerHandler.getObjectMapper().readValue(jsonContent, APIGatewayProxyRequestEvent.class);
        makeHeadersCaseInsensitive(request);
        return this;
    }

    private void makeHeadersCaseInsensitive(APIGatewayProxyRequestEvent request) {
        Headers newHeaders = new Headers();
        if (Objects.nonNull(request.getMultiValueHeaders())) {
            newHeaders.putAll(request.getMultiValueHeaders());
            request.setMultiValueHeaders(newHeaders);
        }
    }

    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public AwsProxyRequestBuilder fromJsonPath(String filePath)
            throws IOException {
        request = LambdaContainerHandler.getObjectMapper().readValue(new File(filePath), APIGatewayProxyRequestEvent.class);
        return this;
    }

    public APIGatewayProxyRequestEvent build() {
        return this.request;
    }

    public InputStream buildStream() {
        try {
            String requestJson = LambdaContainerHandler.getObjectMapper().writeValueAsString(request);
            return new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public InputStream toAlbRequestStream() {
        ApplicationLoadBalancerRequestEvent req = toAlbRequest();
        try {
            String requestJson = LambdaContainerHandler.getObjectMapper().writeValueAsString(req);
            return new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public InputStream toHttpApiV2RequestStream() {
        APIGatewayV2HTTPEvent req = toHttpApiV2Request();
        try {
            String requestJson = LambdaContainerHandler.getObjectMapper().writeValueAsString(req);
            return new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public APIGatewayV2HTTPEvent toHttpApiV2Request() {
        APIGatewayV2HTTPEvent req = new APIGatewayV2HTTPEvent();
        req.setRawPath(request.getPath());
        req.setIsBase64Encoded(request.getIsBase64Encoded());
        req.setBody(request.getBody());
        if (request.getMultiValueHeaders() != null && request.getMultiValueHeaders().containsKey(HttpHeaders.COOKIE)) {
            req.setCookies(Arrays.asList(request.getMultiValueHeaders().get(HttpHeaders.COOKIE).get(0).split(";")));
        }
        req.setHeaders(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        if (request.getMultiValueHeaders() != null) {
            request.getMultiValueHeaders().forEach((key, value) -> {
                if (!HttpHeaders.COOKIE.equals(key)) {
                    req.getHeaders().put(key, value.get(0));
                }
            });
        }
        if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null) {
            if (request.getRequestContext().getIdentity().getCaller() != null) {
                req.getHeaders().put("Referer", request.getRequestContext().getIdentity().getCaller());
            }
            if (request.getRequestContext().getIdentity().getUserAgent() != null) {
                req.getHeaders().put(HttpHeaders.USER_AGENT, request.getRequestContext().getIdentity().getUserAgent());
            }

        }
        if (request.getMultiValueQueryStringParameters() != null) {
            StringBuilder rawQueryString = new StringBuilder();
            request.getMultiValueQueryStringParameters().forEach((k, v) -> {
                for (String s : v) {
                    rawQueryString.append("&");
                    rawQueryString.append(k);
                    rawQueryString.append("=");
                    try {
                        // same terrible hack as the alb() method. Because our spring tests use commas as control characters
                        // we do not encode it
                        rawQueryString.append(URLEncoder.encode(s, "UTF-8").replaceAll("%2C", ","));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            String qs = rawQueryString.toString();
            if (qs.length() > 1) {
                req.setRawQueryString(qs.substring(1));
            }
        }
        req.setRouteKey("$default");
        req.setVersion("2.0");
        req.setStageVariables(request.getStageVariables());

        APIGatewayV2HTTPEvent.RequestContext ctx = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http httpCtx = new APIGatewayV2HTTPEvent.RequestContext.Http();
        httpCtx.setMethod(request.getHttpMethod());
        httpCtx.setPath(request.getPath());
        httpCtx.setProtocol("HTTP/1.1");
        if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null && request.getRequestContext().getIdentity().getSourceIp() != null) {
            httpCtx.setSourceIp(request.getRequestContext().getIdentity().getSourceIp());
        } else {
            httpCtx.setSourceIp("127.0.0.1");
        }
        if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null && request.getRequestContext().getIdentity().getUserAgent() != null) {
            httpCtx.setUserAgent(request.getRequestContext().getIdentity().getUserAgent());
        }
        ctx.setHttp(httpCtx);
        if (request.getRequestContext() != null) {
            ctx.setAccountId(request.getRequestContext().getAccountId());
            ctx.setApiId(request.getRequestContext().getApiId());
            ctx.setDomainName(request.getRequestContext().getApiId() + ".execute-api.us-east-1.apigateway.com");
            ctx.setDomainPrefix(request.getRequestContext().getApiId());
            ctx.setRequestId(request.getRequestContext().getRequestId());
            ctx.setRouteKey("$default");
            ctx.setStage(request.getRequestContext().getStage());
            ctx.setTimeEpoch(request.getRequestContext().getRequestTimeEpoch());
            ctx.setTime(request.getRequestContext().getRequestTime());

            if (request.getRequestContext().getAuthorizer() != null) {
                APIGatewayV2HTTPEvent.RequestContext.Authorizer auth = new APIGatewayV2HTTPEvent.RequestContext.Authorizer();
                APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT jwt = new APIGatewayV2HTTPEvent.RequestContext.Authorizer.JWT();
                // TODO: Anything we should map here?
                jwt.setClaims(new HashMap<>());
                jwt.setScopes(new ArrayList<>());
                auth.setJwt(jwt);
                ctx.setAuthorizer(auth);
            }
        }
        req.setRequestContext(ctx);

        return req;
    }
}