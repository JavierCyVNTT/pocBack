package com.adobe.aem.ecommerce.headless.core.filters;

import org.apache.sling.engine.EngineConstants;
import com.adobe.aem.ecommerce.headless.core.utils.TokenUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Filter.class,
           property = {
                   EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
           })
public class AuthFilter implements Filter {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain chain)
            throws IOException, ServletException {

        SlingHttpServletRequest request =
                (SlingHttpServletRequest) servletRequest;

        HttpServletResponse response =
                (HttpServletResponse) servletResponse;

        String method = request.getMethod();

        String extension =
                request.getRequestPathInfo().getExtension();

        String selector =
                request.getRequestPathInfo().getSelectorString();

        Resource resource = request.getResource();

        String resourceType = resource.getResourceType();

        // Only protect:
        // POST + .json + selector=buy + page resourceType
        logger.debug("resourceType for on Filter", resourceType);
        boolean shouldProtect =
                "POST".equals(method)
                && "json".equals(extension)
                && ("buy".equals(selector) || "logout".equals(selector))
                && "ecommerce/components/page".equals(resourceType);

        if (!shouldProtect) {

            chain.doFilter(request, response);

            return;
        }

        String token = null;

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {

            for (Cookie cookie : cookies) {

                if ("auth-token".equals(cookie.getName())) {

                    token = cookie.getValue();

                    break;
                }
            }
        }

        if (token == null
                || !TokenUtil.validateToken(token)) {

            response.setStatus(401);

            response.setContentType("application/json");

            response.getWriter().write(
                    "{ \"error\": \"unauthorized\" }"
            );

            return;
        }

        // Optional: expose user email

        String email = TokenUtil.extractEmail(token);

        request.setAttribute("userEmail", email);

        chain.doFilter(request, response);
    }
}