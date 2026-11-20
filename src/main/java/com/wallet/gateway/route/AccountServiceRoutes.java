package com.wallet.gateway.route;

import com.wallet.gateway.filter.AuthenticationFilter;
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
public class AccountServiceRoutes {

    /** Every account endpoint requires a valid JWT. */
    @Bean
    public RouterFunction<ServerResponse> accountServiceRoute(AuthenticationFilter authFilter,
                                                              @Value("${account.url}") String accountUrl) {
        return route("account-service")
                .route(RequestPredicates.path("/api/accounts/**"), http())
                .before(uri(accountUrl))
                .filter(authFilter)
                .build();
    }
}
