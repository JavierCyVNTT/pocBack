package com.adobe.aem.ecommerce.headless.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.BufferedReader;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "buy",
        extensions = "json"
)
@ServiceDescription("Ecommerce Login Servlet")
public class BuyServlet extends SlingAllMethodsServlet {

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

        String vehicleId = extractValue(body, "vehicleId");

        if (vehicleId == null || vehicleId.trim().isEmpty()) {

            response.setStatus(400);

            response.getWriter().write(
                    "{ \"error\": \"Vehicle id is required\" }"
            );

            return;
        }


        Resource resource = request.getResource();

        response.setStatus(200);

        response.getWriter().write(
                "{"
                        + "\"success\": true,"
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