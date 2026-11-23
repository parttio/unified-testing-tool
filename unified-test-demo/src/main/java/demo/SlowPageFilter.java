package demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A slow server, for the tests: a page requested with {@code ?slowPage=<ms>} is answered that much later. Flow's
 * own requests carry the location in parameters of their own, so only the page itself is held back.
 */
@Component
public class SlowPageFilter extends OncePerRequestFilter {

    static final String PARAMETER = "slowPage";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String delay = request.getParameter(PARAMETER);
        if (delay != null && delay.matches("\\d{1,4}")) {
            try {
                Thread.sleep(Long.parseLong(delay));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        chain.doFilter(request, response);
    }
}
