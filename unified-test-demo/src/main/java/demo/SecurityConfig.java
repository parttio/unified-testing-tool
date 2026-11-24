package demo;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Vaadin's Spring Security setup: views are protected by their annotations, the login view is public. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class)).build();
    }

    /**
     * Demo users; alice's and bob's passwords equal their names. The barista signs in through the login form
     * ({@code LoginTest}), with a password that is not part of the name, so the masked password hides only itself.
     */
    @Bean
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password("{noop}alice").roles("ADMIN").build(),
                User.withUsername("bob").password("{noop}bob").roles("USER").build(),
                User.withUsername(SignInFormView.USERNAME).password("{noop}" + SignInFormView.PASSWORD).roles("USER")
                        .build());
    }
}
