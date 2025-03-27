package org.moera.search;

import java.security.Security;

import jakarta.inject.Inject;

import com.github.jknack.handlebars.springmvc.HandlebarsViewResolver;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.moera.search.global.CacheControlInterceptor;
import org.moera.search.global.RequestRateInterceptor;
import org.moera.search.ui.helper.HelperSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableScheduling
public class MoeraSearchApplication implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(MoeraSearchApplication.class);

    @Inject
    private RequestRateInterceptor requestRateInterceptor;

    @Inject
    private CacheControlInterceptor cacheControlInterceptor;

    @Inject
    private ApplicationContext applicationContext;

    @Bean
    public HandlebarsViewResolver handlebarsViewResolver() {
        HandlebarsViewResolver resolver = new HandlebarsViewResolver();
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".hbs.html");
        for (Object helperSource : applicationContext.getBeansWithAnnotation(HelperSource.class).values()) {
            log.info("Registering Handlebars helper class {}", helperSource.getClass().getName());
            resolver.registerHelpers(helperSource);
        }
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestRateInterceptor).order(-1);
        registry.addInterceptor(cacheControlInterceptor);
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        SpringApplication.run(MoeraSearchApplication.class, args);
    }

}
