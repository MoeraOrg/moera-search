package org.moera.search.ui;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

@Component
public class UiErrorViewResolver implements ErrorViewResolver {

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        Map<String, Object> errorModel = new HashMap<>(model);
        errorModel.put("status", status.value());
        errorModel.put("comment", status.getReasonPhrase());
        errorModel.put("pageTitle", status.value() + " - " + status.getReasonPhrase() + " | Moera Search");
        return new ModelAndView("fail", errorModel);
    }

}
