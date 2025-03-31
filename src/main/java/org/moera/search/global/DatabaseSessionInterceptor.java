package org.moera.search.global;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.moera.search.data.Database;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DatabaseSessionInterceptor implements HandlerInterceptor {

    @Inject
    private Database database;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        database.open();
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex
    ) {
        database.close();
    }

}
