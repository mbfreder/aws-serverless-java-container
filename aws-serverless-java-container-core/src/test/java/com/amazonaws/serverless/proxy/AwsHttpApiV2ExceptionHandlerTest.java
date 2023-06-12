package com.amazonaws.serverless.proxy;

import com.amazonaws.serverless.exceptions.InvalidRequestEventException;
import com.amazonaws.serverless.exceptions.InvalidResponseObjectException;
import com.amazonaws.serverless.proxy.model.ErrorModel;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AwsHttpApiV2ExceptionHandlerTest {
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request error";
    private static final String INVALID_RESPONSE_MESSAGE = "Invalid response error";
    private AwsHttpApiV2ExceptionHandler exceptionHandler;
    private ObjectMapper objectMapper;


    @BeforeEach
    public void setUp() {
        exceptionHandler = new AwsHttpApiV2ExceptionHandler();
        objectMapper = new ObjectMapper();
    }

    @Test
    void typedHandle_InvalidRequestEventException_500State() {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null));

        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }

    @Test
    void typedHandle_InvalidRequestEventException_responseString()
            throws JsonProcessingException {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null));

        assertNotNull(resp);
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.INTERNAL_SERVER_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void typedHandle_InvalidRequestEventException_jsonContentTypeHeader() {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null));

        assertNotNull(resp);
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
    }

    @Test
    void typedHandle_InvalidResponseObjectException_502State() {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null));

        assertNotNull(resp);
        assertEquals(502, resp.getStatusCode());
    }

    @Test
    void typedHandle_InvalidResponseObjectException_responseString()
            throws JsonProcessingException {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null));

        assertNotNull(resp);
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.GATEWAY_TIMEOUT_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void typedHandle_InvalidResponseObjectException_jsonContentTypeHeader() {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null));

        assertNotNull(resp);
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
    }

    @Test
    void typedHandle_InternalServerErrorException_500State() {
        // Needed to mock InternalServerErrorException because it leverages RuntimeDelegate to set an internal
        // response object.
        InternalServerErrorException mockInternalServerErrorException = Mockito.mock(InternalServerErrorException.class);
        Mockito.when(mockInternalServerErrorException.getMessage()).thenReturn(INTERNAL_SERVER_ERROR_MESSAGE);

        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(mockInternalServerErrorException);

        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }

    @Test
    void typedHandle_InternalServerErrorException_responseString()
            throws JsonProcessingException {
        InternalServerErrorException mockInternalServerErrorException = Mockito.mock(InternalServerErrorException.class);
        Mockito.when(mockInternalServerErrorException.getMessage()).thenReturn(INTERNAL_SERVER_ERROR_MESSAGE);

        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(mockInternalServerErrorException);

        assertNotNull(resp);
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.INTERNAL_SERVER_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void typedHandle_InternalServerErrorException_jsonContentTypeHeader() {
        InternalServerErrorException mockInternalServerErrorException = Mockito.mock(InternalServerErrorException.class);
        Mockito.when(mockInternalServerErrorException.getMessage()).thenReturn(INTERNAL_SERVER_ERROR_MESSAGE);

        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(mockInternalServerErrorException);

        assertNotNull(resp);
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
    }

    @Test
    void typedHandle_NullPointerException_responseObject()
            throws JsonProcessingException {
        APIGatewayV2HTTPResponse resp = exceptionHandler.handle(new NullPointerException());

        assertNotNull(resp);
        assertEquals(502, resp.getStatusCode());
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.GATEWAY_TIMEOUT_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void streamHandle_InvalidRequestEventException_500State()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }

    @Test
    void streamHandle_InvalidRequestEventException_responseString()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.INTERNAL_SERVER_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void streamHandle_InvalidRequestEventException_jsonContentTypeHeader()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidRequestEventException(INVALID_REQUEST_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
    }

    @Test
    void streamHandle_InvalidResponseObjectException_502State()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        assertEquals(502, resp.getStatusCode());
    }

    @Test
    void streamHandle_InvalidResponseObjectException_responseString()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        String body = objectMapper.writeValueAsString(new ErrorModel(AwsProxyExceptionHandler.GATEWAY_TIMEOUT_ERROR));
        assertEquals(body, resp.getBody());
    }

    @Test
    void streamHandle_InvalidResponseObjectException_jsonContentTypeHeader()
            throws IOException {
        ByteArrayOutputStream respStream = new ByteArrayOutputStream();
        exceptionHandler.handle(new InvalidResponseObjectException(INVALID_RESPONSE_MESSAGE, null), respStream);

        assertNotNull(respStream);
        assertTrue(respStream.size() > 0);
        APIGatewayV2HTTPResponse resp = objectMapper.readValue(new ByteArrayInputStream(respStream.toByteArray()), APIGatewayV2HTTPResponse.class);
        assertNotNull(resp);
        assertTrue(resp.getMultiValueHeaders().containsKey(HttpHeaders.CONTENT_TYPE));
        assertEquals(MediaType.APPLICATION_JSON, resp.getMultiValueHeaders().get(HttpHeaders.CONTENT_TYPE).get(0));
    }

    @Test
    void errorMessage_InternalServerError_staticString() {
        assertEquals("Internal Server Error", AwsHttpApiV2ExceptionHandler.INTERNAL_SERVER_ERROR);
    }

    @Test
    void errorMessage_GatewayTimeout_staticString() {
        assertEquals("Gateway timeout", AwsHttpApiV2ExceptionHandler.GATEWAY_TIMEOUT_ERROR);
    }

    @Test
    void getErrorJson_ErrorModel_validJson()
            throws IOException {
        String output = exceptionHandler.getErrorJson(INVALID_RESPONSE_MESSAGE);
        assertNotNull(output);
        ErrorModel error = objectMapper.readValue(output, ErrorModel.class);
        assertNotNull(error);
        assertEquals(INVALID_RESPONSE_MESSAGE, error.getMessage());
    }

    @Test
    void getErrorJson_JsonParsinException_validJson()
            throws IOException {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        JsonProcessingException exception = mock(JsonProcessingException.class);
        when(mockMapper.writeValueAsString(any(Object.class))).thenThrow(exception);

        String output = exceptionHandler.getErrorJson(INVALID_RESPONSE_MESSAGE);
        assertNotNull(output);
        ErrorModel error = objectMapper.readValue(output, ErrorModel.class);
        assertNotNull(error);
        assertEquals(INVALID_RESPONSE_MESSAGE, error.getMessage());
    }
}