package com.pm.apigateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

// This filter validates JWT tokens by calling the authentication service before allowing the request to proceed.
@Component
public class JwtValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final WebClient webClient;

    // Inject WebClient and base URL of the auth service (for flexibility across environments like local, Docker, AWS).
    public JwtValidationGatewayFilterFactory(WebClient.Builder webClientBuilder, @Value("${auth.service.url}") String authServiceUrl){
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
    }

    // Apply the JWT validation filter to the incoming request.
    // "exchange" contains the HTTP request and response, while "chain" manages the next filter in the chain.
    @Override
    public GatewayFilter apply(Object config){
        return (exchange, chain) -> {
            // Extract the Authorization token from the request header.
            String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // If token is missing or invalid (doesn't start with "Bearer "), respond with 401 Unauthorized.
            if(token == null || !token.startsWith("Bearer ")){
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // Set HTTP status to Unauthorized (401).
                return exchange.getResponse().setComplete(); // Complete the response and halt further processing.
            }

            // Validate the token by calling the auth service's /validate endpoint.
            return webClient.get()
                    .uri("/validate")
                    .header(HttpHeaders.AUTHORIZATION, token) // Pass token in the Authorization header.
                    .retrieve() // Perform the GET request.
                    .toBodilessEntity() // We only care about the status of the response.
                    .then(chain.filter(exchange)); // Proceed with the filter chain if token is valid.
        };
    }
}
