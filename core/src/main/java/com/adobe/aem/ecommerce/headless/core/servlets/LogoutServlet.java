package com.adobe.aem.ecommerce.headless.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "logout",
        extensions = "json"
)
public class LogoutServlet extends SlingAllMethodsServlet {

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        Cookie cookie = new Cookie("auth-token", "");

        cookie.setPath("/");

        cookie.setMaxAge(0);

        response.addCookie(cookie);

        response.setContentType("application/json");

        response.getWriter().write(
                "{ \"success\": true }"
        );
    }
}
