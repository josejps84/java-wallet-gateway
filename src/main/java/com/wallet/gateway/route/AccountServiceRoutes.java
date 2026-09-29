package com.wallet.gateway.route;

import com.wallet.gateway.filter.AuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AccountServiceRoutes {

    @Bean
    public RouterFunction<ServerResponse> accountServiceResponse(
            AuthenticationFilter authFilter,
            @Value("${account.host}") String host,
            @Value("${account.port}") String port) {
        return route("account-service")
                .route(RequestPredicates.path("/api/accounts/**"), http())
                .before(uri("http://"+host+":"+port))
//                .before(request -> {
//                    HttpServletRequest servletRequest = request.servletRequest();
//                    String authorization = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
//
//                    log.info("Authorization header: {}", authorization);
//                    if (authorization != null && !authorization.isBlank()) {
//                        return ServerRequest.from(request)
//                                .header(HttpHeaders.AUTHORIZATION, authorization)
//                                .build();
//                    }
//                    return request;
//                })
                .filter(authFilter)
                .build();
    }
}
