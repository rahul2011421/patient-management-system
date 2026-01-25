package com.pm.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// This class globally handles exceptions related to JWT validation in the API Gateway.
@RestControllerAdvice
public class JwtValidationException {

    // This method handles Unauthorized exceptions (401), specifically WebClientResponseException.Unauthorized.
    // It is triggered when the authentication service responds with a 401 status code.
    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    public Mono<Void> handleUnauthorizedException(ServerWebExchange exchange){
        // Set the HTTP response status to 401 Unauthorized.
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        // Complete the response, ensuring no further processing occurs after sending the 401.
        return exchange.getResponse().setComplete();
    }
}
