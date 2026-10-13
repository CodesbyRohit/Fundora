package com.fundora.config;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Module III (Servlets & Filter API):
 * AuthFilter intercepts incoming HTTP requests, manages session state,
 * and ensures user authentication before accessing ledger, expense and
 * state-changing REST routes (settlements, nudges, new expenses).
 */
@WebFilter(urlPatterns = {"/expenses/*", "/ledger/*", "/dashboard/*", "/api/settle/*", "/api/nudge", "/api/expenses/*"})
public class AuthFilter implements Filter {

    public static final String SESSION_USER_ID = "currentUserId";
    public static final String USER_ID_HEADER = "X-Fundora-User-Id";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Lifecycle init
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Retrieve existing session without creating a new one
        HttpSession session = httpRequest.getSession(false);
        Long loggedInUserId = (session != null) ? (Long) session.getAttribute(SESSION_USER_ID) : null;

        // Header-based identity is a convenience for REST API testing (curl/Postman) only
        if (loggedInUserId == null) {
            Long headerUserId = parseUserId(httpRequest.getHeader(USER_ID_HEADER));
            if (headerUserId != null) {
                httpRequest.getSession(true).setAttribute(SESSION_USER_ID, headerUserId);
                loggedInUserId = headerUserId;
            }
        }

        if (loggedInUserId != null) {
            chain.doFilter(request, response);
        } else if (isApiRequest(httpRequest)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Please sign in (or send the " + USER_ID_HEADER + " header) to use this endpoint.\"}");
        } else {
            // Redirect unauthenticated web browser requests to /login
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
        }
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void destroy() {
        // Lifecycle destroy
    }
}
