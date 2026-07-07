package com.adobe.aem.ecommerce.headless.core.servlets;

import com.adobe.aem.ecommerce.headless.core.utils.TokenUtil;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import javax.servlet.http.Cookie;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "login",
        extensions = "json"
)
@ServiceDescription("Ecommerce Login Servlet")
public class LoginServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        BufferedReader reader = request.getReader();

        StringBuilder sb = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
        sb.append(line);
        }

        String body = request.getReader()
                .lines()
                .reduce("", (accumulator, actual) -> accumulator + actual);

        String email = extractValue(body, "email");
        String password = extractValue(body, "password");

        if (email == null || email.trim().isEmpty()
        || password == null || password.trim().isEmpty()) {

            response.setStatus(400);

            response.getWriter().write(
                    "{ \"error\": \"email and password are required\" }"
            );

            return;
        }

        // Fake auth
        if (!"admin@test.com".equals(email)
                || !"admin123".equals(password)) {

            response.setStatus(401);

            response.getWriter().write(
                    "{ \"error\": \"invalid credentials\" }"
            );

            return;
        }

        String token = TokenUtil.generateToken(email);

        Resource resource = request.getResource();

        Cookie cookie = new Cookie("auth-token", token);

        cookie.setHttpOnly(true);
        cookie.setPath("/");

        // solo en HTTPS real
        // cookie.setSecure(true);

        cookie.setMaxAge(60 * 60);

        response.addCookie(cookie);

        response.setStatus(200);

        response.getWriter().write(
                "{"
                        + "\"success\": true,"
                        + "\"token\": \"" + token + "\","
                        + "\"expiresIn\": 3600,"
                        + "\"email\": \"" + email + "\","
                        + "\"page\": \"" + resource.getPath() + "\""
                        + "}"
        );
    }

    private String extractValue(String json, String key) {

                String pattern = "\"" + key + "\":";

                int start = json.indexOf(pattern);

                if (start == -1) {
                return null;
                }

                start = json.indexOf("\"", start + pattern.length());

                int end = json.indexOf("\"", start + 1);

                if (start == -1 || end == -1) {
                return null;
                }

                return json.substring(start + 1, end);
        }
}