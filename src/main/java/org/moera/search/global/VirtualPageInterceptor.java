package org.moera.search.global;

import java.io.IOException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.moera.lib.UniversalLocation;
import org.moera.search.config.Config;
import org.moera.search.util.UriUtil;
import org.moera.search.util.VirtualPageHeader;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriComponents;

@Component
public class VirtualPageInterceptor implements HandlerInterceptor {

    @Inject
    private Config config;

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler
    ) throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        VirtualPage virtualPage = ((HandlerMethod) handler).getMethodAnnotation(VirtualPage.class);
        if (virtualPage == null) {
            return true;
        }
        VirtualPageHeader.put(response, config.getNodeName(), virtualPage.value());

        if (request.getHeader("X-Accept-Moera") != null) {
            // No redirect and no content, because a Moera client is making this request
            return false;
        }

        String href = virtualPage.value();
        if (ObjectUtils.isEmpty(href)) {
            UriComponents uriComponents = UriUtil.createBuilderFromRequest(request).build();
            href = uriComponents.toUriString();
        }
        response.sendRedirect(UniversalLocation.redirectTo(config.getNodeName(), href));

        return false;
    }

}
