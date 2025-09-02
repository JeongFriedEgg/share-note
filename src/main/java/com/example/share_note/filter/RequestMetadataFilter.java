package com.example.share_note.filter;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Component
public class RequestMetadataFilter implements WebFilter {

    private static final UserAgentAnalyzer userAgentAnalyzer;

    static {
        userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .withCache(1000)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");

        String userAgentString = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("User-Agent"))
                .orElse("unknown");

        UserAgent agent = userAgentAnalyzer.parse(userAgentString);
        String deviceName = Optional.ofNullable(agent.getValue("DeviceName")).orElse("unknown");
        String osName = Optional.ofNullable(agent.getValue("OperatingSystemName")).orElse("unknown");
        String browserName = Optional.ofNullable(agent.getValue("AgentName")).orElse("unknown");

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put("clientIp", clientIp))
                .contextWrite(ctx -> ctx.put("deviceName", deviceName))
                .contextWrite(ctx -> ctx.put("osName", osName))
                .contextWrite(ctx -> ctx.put("browserName", browserName));
    }
}
