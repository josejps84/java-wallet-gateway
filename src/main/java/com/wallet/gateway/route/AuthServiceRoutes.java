package com.wallet.gateway.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class AuthServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> authServiceResponse(
            @Value("${auth.host}") String host,
            @Value("${auth.port}") String port) {
        return route("auth-service")
                .route(RequestPredicates.path("/api/users/**"), http())
                .before(uri("http://"+host+":"+port))
                .build();
    }
}