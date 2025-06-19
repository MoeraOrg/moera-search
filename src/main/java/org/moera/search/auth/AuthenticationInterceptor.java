package org.moera.search.auth;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.Locale;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.moera.lib.node.types.Result;
import org.moera.lib.node.types.Scope;
import org.moera.search.util.UriUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);

    @Inject
    private AuthenticationManager authenticationManager;

    @Inject
    private RequestContext requestContext;

    @Inject
    private MessageSource messageSource;

    @Inject
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler
    ) throws IOException {
        try {
            processAuthParameters(request);
            return true;
        } catch (AuthenticationException e) {
            handleError(response, HttpStatus.FORBIDDEN, "authentication.required", "Bearer realm=\"Node\"");
            return false;
        }
    }

    private void handleError(
        HttpServletResponse response, HttpStatus status, String errorCode, String wwwAuthHeader
    ) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthHeader);
        response.setStatus(status.value());
        String message = messageSource.getMessage(errorCode, null, Locale.getDefault());
        objectMapper.writeValue(new PrintWriter(response.getOutputStream()), new Result(errorCode, message));
    }

    private AuthSecrets extractSecrets(HttpServletRequest request) {
        String auth = request.getParameter("auth");
        if (!ObjectUtils.isEmpty(auth)) {
            return new AuthSecrets(auth);
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!ObjectUtils.isEmpty(authHeader)) {
            String[] parts = StringUtils.split(authHeader, " ");
            if (parts != null && parts[0].trim().equalsIgnoreCase("bearer")) {
                return new AuthSecrets(parts[1].trim());
            }
        }
        return new AuthSecrets();
    }

    private void processAuthParameters(HttpServletRequest request) throws UnknownHostException {
        AuthSecrets secrets = extractSecrets(request);
        CarteAuthInfo carteAuthInfo = authenticationManager.getCarte(secrets.carte, UriUtil.remoteAddress(request));
        if (carteAuthInfo != null) {
            String clientName = carteAuthInfo.getClientName();
            requestContext.setClientName(clientName);
            requestContext.setClientScope(carteAuthInfo.getClientScope());
        }
        logAuthStatus();
    }

    private void logAuthStatus() {
        String clientName = requestContext.getClientName(Scope.IDENTIFY);
        if (clientName != null) {
            log.info("Authorized with node name {}", clientName);
        }
        if (clientName != null && !requestContext.hasClientScope(Scope.ALL)) {
            log.info("Client scope is ({})", String.join(", ", Scope.toValues(requestContext.getClientScope())));
        }
    }

}
