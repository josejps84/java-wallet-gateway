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

    /** Only login and registration are exposed publicly; anything else under /api/users is not routed. */
    @Bean
    public RouterFunction<ServerResponse> authServiceRoute(@Value("${auth.url}") String authUrl) {
        return route("auth-service")
                .route(RequestPredicates.POST("/api/users/login").or(RequestPredicates.POST("/api/users/register")),
                        http())
                .before(uri(authUrl))
                .build();
    }
}
