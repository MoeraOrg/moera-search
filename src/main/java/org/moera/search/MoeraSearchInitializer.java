package org.moera.search;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import com.twelvemonkeys.servlet.image.IIOProviderContextListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.WebApplicationInitializer;

@Configuration
public class MoeraSearchInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        servletContext.addListener(IIOProviderContextListener.class);
    }

}
