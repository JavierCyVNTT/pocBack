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
import java.util.ArrayList;
import java.util.List;

@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "ai-quote",
        extensions = "json"
)
@ServiceDescription("AI Car Quote Servlet")
public class AiQuoteServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AiQuoteServlet.class);

    private static final int MAX_IMAGES = 5;

    @Reference
    private GeminiService geminiService;

    private static final String SYSTEM_PROMPT =
        "You are a professional car appraiser. " +
        "Analyze the provided car description and photos to estimate its market value in USD.\n" +
        "Return ONLY a valid JSON object with no explanation or markdown. Use these exact fields:\n" +
        "- minPriceUSD (number): minimum estimated price in USD\n" +
        "- maxPriceUSD (number): maximum estimated price in USD\n" +
        "- condition (string): one of excellent | good | fair | poor\n" +
        "- reasoning (string): 1-2 sentences explaining your estimate\n\n" +
        "Car description: ";

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = readBody(request);
        String description = extractStringValue(body, "description");

        if (description == null || description.trim().isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"'description' field is required\"}");
            return;
        }

        List<String[]> images = extractImages(body);

        if (images.size() > MAX_IMAGES) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"Maximum " + MAX_IMAGES + " images allowed\"}");
            return;
        }

        try {
            String geminiText;
            if (images.isEmpty()) {
                geminiText = geminiService.generateText(SYSTEM_PROMPT + description);
            } else {
                geminiText = geminiService.generateWithImages(SYSTEM_PROMPT + description, images);
            }

            String quoteJson = extractJsonBlock(geminiText);
            long minUSD = extractLongValue(quoteJson, "minPriceUSD");
            long maxUSD = extractLongValue(quoteJson, "maxPriceUSD");
            String condition = extractStringValue(quoteJson, "condition");
            String reasoning = extractStringValue(quoteJson, "reasoning");

            String result = "{"
                + "\"success\":true,"
                + "\"quote\":{"
                + "\"minPriceUSD\":" + minUSD + ","
                + "\"maxPriceUSD\":" + maxUSD + ","
                + "\"condition\":\"" + (condition != null ? condition : "unknown") + "\","
                + "\"reasoning\":" + geminiService.jsonEscape(reasoning != null ? reasoning : "")
                + "}"
                + "}";

            response.setStatus(200);
            response.getWriter().write(result);

        } catch (Exception e) {
            LOG.error("AI quote failed", e);
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"AI service temporarily unavailable\"}");
        }
    }

    /**
     * Extracts images from the JSON body.
     * Accepts both raw base64 and data-URL format (data:image/jpeg;base64,...).
     * Returns list of String[2]: { base64data, mimeType }.
     */
    private List<String[]> extractImages(String json) {
        List<String[]> images = new ArrayList<>();
        String marker = "\"images\":[";
        int idx = json.indexOf(marker);
        if (idx == -1) return images;

        int pos = idx + marker.length();
        while (pos < json.length()) {
            while (pos < json.length() && json.charAt(pos) != '"' && json.charAt(pos) != ']') {
                pos++;
            }
            if (pos >= json.length() || json.charAt(pos) == ']') break;

            pos++;
            int start = pos;
            while (pos < json.length() && json.charAt(pos) != '"') {
                pos++;
            }
            if (pos >= json.length()) break;

            String raw = json.substring(start, pos);
            pos++;

            String mimeType = detectMimeType(raw);
            String base64Data = stripDataUrlPrefix(raw);
            images.add(new String[]{base64Data, mimeType});
        }
        return images;
    }

    private String detectMimeType(String raw) {
        if (raw.startsWith("data:image/png")) return "image/png";
        if (raw.startsWith("data:image/webp")) return "image/webp";
        return "image/jpeg";
    }

    private String stripDataUrlPrefix(String raw) {
        int commaIdx = raw.indexOf(',');
        return commaIdx >= 0 ? raw.substring(commaIdx + 1) : raw;
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

    private long extractLongValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return 0;
        int start = idx + pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
            end++;
        }
        if (start == end) return 0;
        try {
            return (long) Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
