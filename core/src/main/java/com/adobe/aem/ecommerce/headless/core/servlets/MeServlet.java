package com.adobe.aem.ecommerce.headless.core.servlets;


import com.adobe.aem.ecommerce.headless.core.utils.TokenUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_GET,
        selectors = "me",
        extensions = "json"
)
@ServiceDescription("Ecommerce Me Servlet")
public class MeServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

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

        // ❌ No token
        if (token == null) {
            response.setStatus(401);
            response.getWriter().write(
                    "{ \"success\": false, \"error\": \"no token\" }"
            );
            return;
        }

        // ❌ Token inválido
        if (!TokenUtil.validateToken(token)) {
            response.setStatus(401);
            response.getWriter().write(
                    "{ \"success\": false, \"error\": \"invalid token\" }"
            );
            return;
        }

        // ✅ Token válido
        String email = TokenUtil.extractEmail(token);

        response.setStatus(200);
        response.getWriter().write(
                "{"
                        + "\"success\": true,"
                        + "\"email\": \"" + email + "\""
                        + "}"
        );
    }
}
