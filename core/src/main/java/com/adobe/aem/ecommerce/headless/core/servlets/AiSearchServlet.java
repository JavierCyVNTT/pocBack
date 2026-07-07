package com.adobe.aem.ecommerce.headless.core.servlets;

import com.adobe.aem.ecommerce.headless.core.services.GeminiService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.IOException;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "ai-search",
        extensions = "json"
)
@ServiceDescription("AI Car Search Servlet")
public class AiSearchServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AiSearchServlet.class);

    @Reference
    private GeminiService geminiService;

    private static final String SYSTEM_PROMPT =
        "You are a car search assistant for an ecommerce platform. " +
        "Parse the user's search query and extract structured filters as a JSON object.\n" +
        "Return ONLY a valid JSON object. Do not include any explanation or markdown.\n" +
        "Include only the fields that are clearly mentioned or implied in the query.\n\n" +
        "Available fields:\n" +
        "- brand (string): car brand, e.g. \"Toyota\", \"Ford\"\n" +
        "- model (string): car model, e.g. \"Corolla\", \"Ranger\"\n" +
        "- type (string): body type — SUV, sedan, pickup, hatchback, coupe, minivan, truck\n" +
        "- minYear (number): minimum year\n" +
        "- maxYear (number): maximum year\n" +
        "- minPriceUSD (number): minimum price in USD\n" +
        "- maxPriceUSD (number): maximum price in USD\n" +
        "- maxKm (number): maximum kilometers driven\n" +
        "- transmission (string): automatic or manual\n" +
        "- fuel (string): gasoline, diesel, electric, hybrid\n" +
        "- color (string): car color\n" +
        "- keywords (array of strings): any other relevant terms from the query\n\n" +
        "User query: ";

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = readBody(request);
        String query = extractStringValue(body, "query");

        if (query == null || query.trim().isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"'query' field is required\"}");
            return;
        }

        try {
            String geminiText = geminiService.generateText(SYSTEM_PROMPT + query);
            String filtersJson = extractJsonBlock(geminiText);
            response.setStatus(200);
            response.getWriter().write("{\"success\":true,\"filters\":" + filtersJson + "}");
        } catch (Exception e) {
            LOG.error("AI search failed", e);
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"AI service temporarily unavailable\"}");
        }
    }

    private String readBody(SlingHttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Extracts the first complete JSON object found in the text.
     * Handles cases where Gemini wraps its response in markdown code blocks.
     */
    private String extractJsonBlock(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = json.indexOf('"', idx + pattern.length());
        if (start == -1) return null;
        start++;
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
