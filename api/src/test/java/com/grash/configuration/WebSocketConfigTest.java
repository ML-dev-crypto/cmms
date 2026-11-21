package com.grash.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    private static final String RAILWAY_ORIGIN = "https://hospital-cmms-web-production.up.railway.app";

    @Mock
    private StompEndpointRegistry stompEndpointRegistry;
    @Mock
    private StompWebSocketEndpointRegistration endpointRegistration;
    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @Test
    void registerStompEndpoints_allowsSameOriginsAsRestCors() {
        AllowedOriginsResolver resolver = new AllowedOriginsResolver();
        ReflectionTestUtils.setField(resolver, "frontendUrl", "http://localhost:3000/");
        ReflectionTestUtils.setField(resolver, "frontendHomeUrl", "");
        ReflectionTestUtils.setField(resolver, "allowedOrigins", RAILWAY_ORIGIN + ", *");
        WebSocketConfig webSocketConfig = new WebSocketConfig(resolver);

        when(stompEndpointRegistry.addEndpoint("/ws")).thenReturn(endpointRegistration);
        when(endpointRegistration.setAllowedOrigins(org.mockito.ArgumentMatchers.any(String[].class)))
                .thenReturn(endpointRegistration);
        when(endpointRegistration.withSockJS()).thenReturn(mock(SockJsServiceRegistration.class));

        webSocketConfig.registerStompEndpoints(stompEndpointRegistry);

        ArgumentCaptor<String[]> originsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(endpointRegistration).setAllowedOrigins(originsCaptor.capture());
        verify(endpointRegistration).withSockJS();

        String[] origins = originsCaptor.getValue();
        assertArrayEquals(new String[]{"http://localhost:3000", RAILWAY_ORIGIN}, origins);
        assertFalse(Arrays.asList(origins).contains("*"));
    }

    @Test
    void configureMessageBroker_keepsBrokerAndPrefixes() {
        WebSocketConfig webSocketConfig = new WebSocketConfig(new AllowedOriginsResolver());

        webSocketConfig.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/notifications", "/exports", "/imports", "/user");
        verify(messageBrokerRegistry).setApplicationDestinationPrefixes("/app");
        verify(messageBrokerRegistry).setUserDestinationPrefix("/user");
    }
}
