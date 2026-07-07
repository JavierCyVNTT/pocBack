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

/**
 * Takes the seller-reviewed/corrected vehicle data (after the preliminary range quote from
 * AiQuoteServlet) and asks Gemini for a single, precise recommended price to publish with.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "ai-quote-final",
        extensions = "json"
)
@ServiceDescription("AI Final Car Quote Servlet")
public class AiFinalQuoteServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AiFinalQuoteServlet.class);

    @Reference
    private GeminiService geminiService;

    private static final String SYSTEM_PROMPT =
        "You are a professional car appraiser. " +
        "The seller has confirmed the following vehicle data, including legal/documentation " +
        "disclosures. Factor in condition, equipment, damage/wear and legal status (pending " +
        "debts or fines and an invalid technical inspection reduce the price) to " +
        "provide a single, precise recommended market price in USD, not a range.\n" +
        "Return ONLY a valid JSON object with no explanation or markdown. Use these exact fields:\n" +
        "- recommendedPriceUSD (number): a single recommended market price in USD\n" +
        "- reasoning (string): 1-2 sentences explaining your estimate\n\n" +
        "Vehicle data: ";

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = readBody(request);

        String brandName = extractStringValue(body, "brandName");
        String modelName = extractStringValue(body, "modelName");

        if (isBlank(brandName) || isBlank(modelName)) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"'brandName' and 'modelName' are required\"}");
            return;
        }

        String versionName = extractStringValue(body, "versionName");
        String lineName = extractStringValue(body, "lineName");
        String typeName = extractStringValue(body, "typeName");
        String condition = extractStringValue(body, "condition");
        String equipmentLevel = extractStringValue(body, "equipmentLevel");
        String color = extractStringValue(body, "color");
        String engineDescription = extractStringValue(body, "engineDescription");
        String fuelType = extractStringValue(body, "fuelType");
        String transmission = extractStringValue(body, "transmission");
        String traction = extractStringValue(body, "traction");
        String location = extractStringValue(body, "location");
        String damageDescription = extractStringValue(body, "damageDescription");
        String wearDescription = extractStringValue(body, "wearDescription");
        String comfortFeatures = extractStringValue(body, "comfortFeatures");
        String safetyFeatures = extractStringValue(body, "safetyFeatures");
        long year = extractLongValue(body, "year");
        long km = extractLongValue(body, "km");
        long ownersCount = extractLongValue(body, "ownersCount");
        long seats = extractLongValue(body, "seats");
        long doors = extractLongValue(body, "doors");
        long airbagsCount = extractLongValue(body, "airbagsCount");
        boolean maintenanceUpToDate = extractBooleanValue(body, "maintenanceUpToDate");
        boolean hasDamage = extractBooleanValue(body, "hasDamage");
        boolean hasSpareKeyAndManual = extractBooleanValue(body, "hasSpareKeyAndManual");

        // Disclosure-only context: never persisted to the public catalog, but legitimately
        // affects what a buyer would actually pay, so it is taken into account here.
        boolean isOwner = extractBooleanValue(body, "isOwner");
        boolean hasDebts = extractBooleanValue(body, "hasDebts");
        boolean technicalInspectionValid = extractBooleanValue(body, "technicalInspectionValid");

        String vehicleSummary = "Brand: " + brandName
            + ", Model: " + modelName
            + (isBlank(versionName) ? "" : ", Version: " + versionName)
            + (isBlank(lineName) ? "" : ", Line: " + lineName)
            + (isBlank(typeName) ? "" : ", Type: " + typeName)
            + (isBlank(equipmentLevel) ? "" : ", Equipment level: " + equipmentLevel)
            + (isBlank(color) ? "" : ", Color: " + color)
            + ", Year: " + year
            + ", Mileage: " + km + " km"
            + (isBlank(engineDescription) ? "" : ", Engine: " + engineDescription)
            + (isBlank(fuelType) ? "" : ", Fuel: " + fuelType)
            + (isBlank(transmission) ? "" : ", Transmission: " + transmission)
            + (isBlank(traction) ? "" : ", Traction: " + traction)
            + ", Doors: " + doors
            + ", Seats: " + seats
            + ", Previous owners: " + ownersCount
            + (isBlank(location) ? "" : ", Location: " + location)
            + ", Maintenance up to date: " + maintenanceUpToDate
            + ", Has damage: " + hasDamage
            + (isBlank(damageDescription) ? "" : ", Damage detail: " + damageDescription)
            + (isBlank(wearDescription) ? "" : ", Wear: " + wearDescription)
            + (isBlank(comfortFeatures) ? "" : ", Comfort features: " + comfortFeatures)
            + ", Airbags: " + airbagsCount
            + (isBlank(safetyFeatures) ? "" : ", Safety features: " + safetyFeatures)
            + ", Has spare key and manual: " + hasSpareKeyAndManual
            + ", Seller is the direct owner: " + isOwner
            + ", Has pending debts/fines: " + hasDebts
            + ", Technical inspection valid: " + technicalInspectionValid
            + (isBlank(condition) ? "" : ", Condition: " + condition);

        try {
            String geminiText = geminiService.generateText(SYSTEM_PROMPT + vehicleSummary);
            String quoteJson = extractJsonBlock(geminiText);
            long recommendedPriceUSD = extractLongValue(quoteJson, "recommendedPriceUSD");
            String reasoning = extractStringValue(quoteJson, "reasoning");

            String result = "{"
                + "\"success\":true,"
                + "\"quote\":{"
                + "\"recommendedPriceUSD\":" + recommendedPriceUSD + ","
                + "\"reasoning\":" + geminiService.jsonEscape(reasoning != null ? reasoning : "")
                + "}"
                + "}";

            response.setStatus(200);
            response.getWriter().write(result);

        } catch (Exception e) {
            LOG.error("AI final quote failed", e);
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"AI service temporarily unavailable\"}");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private boolean extractBooleanValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return false;
        int start = idx + pattern.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        return json.regionMatches(start, "true", 0, 4);
    }
}
