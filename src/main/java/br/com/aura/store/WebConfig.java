package br.com.aura.store;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** URLs amigáveis para as páginas da loja (os arquivos ficam em static/*.html). */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String page : new String[]{"entrar", "cadastro", "conta", "admin"}) {
            registry.addViewController("/" + page).setViewName("forward:/" + page + ".html");
        }
    }
}
