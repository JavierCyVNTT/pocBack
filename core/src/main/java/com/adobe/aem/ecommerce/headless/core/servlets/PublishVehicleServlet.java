package com.adobe.aem.ecommerce.headless.core.servlets;

import com.day.cq.dam.api.Asset;
import com.day.cq.dam.api.AssetManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a real vehicle Content Fragment under /content/dam/ecommerce/vehiculos-disponibles
 * so an accepted AI quote shows up in the catalog alongside the rest of the vehicles for sale.
 * Legal/disclosure-only fields (ownership, debts, technical inspection) are intentionally not
 * accepted here: they are seller disclosures for advisor review, not public catalog data.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(
        resourceTypes = "ecommerce/components/page",
        methods = HttpConstants.METHOD_POST,
        selectors = "publish-vehicle",
        extensions = "json"
)
@ServiceDescription("Publish Vehicle Servlet")
public class PublishVehicleServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(PublishVehicleServlet.class);

    private static final String VEHICLES_ROOT = "/content/dam/ecommerce/vehiculos-disponibles";
    private static final String IMAGES_ROOT = "/content/dam/ecommerce/imagenes";
    private static final String CF_MODEL_PATH = "/conf/ecommerce/settings/dam/cfm/models/vehicle";

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String body = readBody(request);
        double price = extractDoubleValue(body, "price");
        Map<String, Object> fields = parseVehicleFields(body);

        String brandName = (String) fields.get("brandName");
        String modelName = (String) fields.get("modelName");

        if (isBlank(brandName) || isBlank(modelName) || price <= 0) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"'brandName', 'modelName' and 'price' are required\"}");
            return;
        }

        List<String> images = extractStringArray(body, "images");
        ResourceResolver resolver = request.getResourceResolver();
        long year = fields.containsKey("year") ? (Long) fields.get("year") : 0;
        String slug = buildSlug(brandName, modelName, year);

        try {
            String outsideImagePath = images.size() > 0 ? createImageAsset(resolver, slug + "-ext", images.get(0)) : null;
            String insideImagePath = images.size() > 1
                    ? createImageAsset(resolver, slug + "-int", images.get(1))
                    : outsideImagePath;

            if (outsideImagePath != null) {
                fields.put("outsideImage", outsideImagePath);
            }
            if (insideImagePath != null) {
                fields.put("insideImage", insideImagePath);
            }
            fields.put("price", price);

            String vehiclePath = createVehicleFragment(resolver, slug, fields);

            resolver.commit();

            response.setStatus(200);
            response.getWriter().write(
                    "{\"success\":true,\"vehicle\":{\"id\":\"" + slug + "\",\"path\":\"" + vehiclePath + "\"}}"
            );
        } catch (Exception e) {
            LOG.error("Publishing vehicle failed", e);
            response.setStatus(500);
            response.getWriter().write("{\"error\":\"No se pudo publicar el vehiculo\"}");
        }
    }

    /**
     * Extracts every public, catalog-facing vehicle field from the request body. Missing/blank
     * values are simply omitted so the fragment only ends up with the data the seller provided.
     */
    private Map<String, Object> parseVehicleFields(String body) {
        Map<String, Object> fields = new LinkedHashMap<>();

        putString(fields, body, "brandName");
        putString(fields, body, "modelName");
        putString(fields, body, "versionName");
        putString(fields, body, "lineName");
        putString(fields, body, "typeName");
        putString(fields, body, "equipmentLevel");
        putString(fields, body, "color");
        putString(fields, body, "engineDescription");
        putString(fields, body, "fuelType");
        putString(fields, body, "transmission");
        putString(fields, body, "traction");
        putString(fields, body, "location");
        putString(fields, body, "damageDescription");
        putString(fields, body, "wearDescription");
        putString(fields, body, "comfortFeatures");
        putString(fields, body, "safetyFeatures");

        putLongIfPositive(fields, body, "year");
        putLongIfPositive(fields, body, "km");
        putLongIfPositive(fields, body, "ownersCount");
        putLongIfPositive(fields, body, "seats");
        putLongIfPositive(fields, body, "doors");
        putLongIfPositive(fields, body, "airbagsCount");

        fields.put("maintenanceUpToDate", extractBooleanValue(body, "maintenanceUpToDate"));
        fields.put("hasDamage", extractBooleanValue(body, "hasDamage"));
        fields.put("hasSpareKeyAndManual", extractBooleanValue(body, "hasSpareKeyAndManual"));

        return fields;
    }

    private void putString(Map<String, Object> fields, String body, String key) {
        String value = extractStringValue(body, key);
        if (!isBlank(value)) {
            fields.put(key, value);
        }
    }

    private void putLongIfPositive(Map<String, Object> fields, String body, String key) {
        long value = extractLongValue(body, key);
        if (value > 0) {
            fields.put(key, value);
        }
    }

    private String createVehicleFragment(ResourceResolver resolver, String slug, Map<String, Object> fields)
            throws Exception {

        Resource vehiclesRoot = resolver.getResource(VEHICLES_ROOT);
        if (vehiclesRoot == null) {
            throw new IllegalStateException("Vehicles root not found: " + VEHICLES_ROOT);
        }

        String brandName = (String) fields.get("brandName");
        String modelName = (String) fields.get("modelName");

        Map<String, Object> assetProps = new LinkedHashMap<>();
        assetProps.put("jcr:primaryType", "dam:Asset");
        assetProps.put("jcr:mixinTypes", new String[]{"mix:referenceable"});
        Resource assetResource = resolver.create(vehiclesRoot, slug, assetProps);

        Map<String, Object> contentProps = new LinkedHashMap<>();
        contentProps.put("jcr:primaryType", "dam:AssetContent");
        contentProps.put("jcr:title", brandName + " " + modelName);
        contentProps.put("contentFragment", true);
        Resource contentResource = resolver.create(assetResource, "jcr:content", contentProps);

        Map<String, Object> dataProps = new LinkedHashMap<>();
        dataProps.put("jcr:primaryType", "nt:unstructured");
        dataProps.put("cq:model", CF_MODEL_PATH);
        Resource dataResource = resolver.create(contentResource, "data", dataProps);

        // Image reference fields must be genuine JCR PATH properties for AEM's GraphQL
        // schema to resolve them as ImageRef; a plain String property resolves to an
        // empty object. resolver.create() can only infer STRING from a Java String, so
        // these two are set separately below via the JCR API with an explicit type.
        String outsideImagePath = (String) fields.remove("outsideImage");
        String insideImagePath = (String) fields.remove("insideImage");

        Map<String, Object> masterProps = new LinkedHashMap<>(fields);
        masterProps.put("jcr:primaryType", "nt:unstructured");
        masterProps.put("destacado", false);
        masterProps.put("masVendidos", false);
        Resource masterResource = resolver.create(dataResource, "master", masterProps);

        Node masterNode = masterResource.adaptTo(Node.class);
        if (masterNode != null) {
            if (outsideImagePath != null) {
                masterNode.setProperty("outsideImage", outsideImagePath, PropertyType.PATH);
            }
            if (insideImagePath != null) {
                masterNode.setProperty("insideImage", insideImagePath, PropertyType.PATH);
            }
        }

        // Mirrors AEM's own Content Fragment index (type-prefixed property names) so the new
        // vehicle is immediately matched by filtered GraphQL queries, without waiting for the
        // async DAM indexing listener to catch up, and so the AI-powered search can filter on
        // every one of these attributes the same way it already does for brand/model/type.
        Map<String, Object> indexedDataProps = new LinkedHashMap<>();
        indexedDataProps.put("jcr:primaryType", "nt:unstructured");
        Resource indexedDataResource = resolver.create(contentResource, "indexedData", indexedDataProps);

        Map<String, Object> indexedMasterProps = new LinkedHashMap<>();
        indexedMasterProps.put("jcr:primaryType", "nt:unstructured");
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            String prefixedName = indexedPropertyName(entry.getKey(), value);
            if (prefixedName != null) {
                indexedMasterProps.put(prefixedName, value);
            }
        }
        indexedMasterProps.put("boolean@destacado", false);
        indexedMasterProps.put("boolean@masVendidos", false);
        resolver.create(indexedDataResource, "master", indexedMasterProps);

        return assetResource.getPath();
    }

    private String indexedPropertyName(String key, Object value) {
        if ("outsideImage".equals(key) || "insideImage".equals(key)) {
            return null;
        }
        if (value instanceof Long) {
            return "long@" + key;
        }
        if (value instanceof Double) {
            return "double@" + key;
        }
        if (value instanceof Boolean) {
            return "boolean@" + key;
        }
        if (value instanceof String) {
            return "string@" + key;
        }
        return null;
    }

    private String createImageAsset(ResourceResolver resolver, String name, String dataUrl) throws Exception {
        String mimeType = detectMimeType(dataUrl);
        String base64Data = stripDataUrlPrefix(dataUrl);
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        String extension = mimeType.equals("image/png") ? "png" : mimeType.equals("image/webp") ? "webp" : "jpg";
        String assetPath = IMAGES_ROOT + "/" + name + "." + extension;

        AssetManager assetManager = resolver.adaptTo(AssetManager.class);
        if (assetManager == null) {
            throw new IllegalStateException("AssetManager not available");
        }
        Asset asset = assetManager.createAsset(assetPath, new ByteArrayInputStream(bytes), mimeType, true);

        // AEM's asset-processing workflow extracts dam:MIMEtype from formats it has a
        // decoder for (e.g. png/jpeg); this SDK instance has none for webp, so that
        // property never gets set and GraphQL's ImageRef resolution then treats the
        // asset as unresolved (empty object instead of { _path }). Set it explicitly
        // so every format works the same way regardless of decoder support.
        Resource metadataResource = resolver.getResource(assetPath + "/jcr:content/metadata");
        Node metadataNode = metadataResource != null ? metadataResource.adaptTo(Node.class) : null;
        if (metadataNode != null && !metadataNode.hasProperty("dam:MIMEtype")) {
            metadataNode.setProperty("dam:MIMEtype", mimeType);
        }

        return asset.getPath();
    }

    private String buildSlug(String brandName, String modelName, long year) {
        String base = (brandName + "-" + modelName + (year > 0 ? "-" + year : ""))
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return base + "-" + System.currentTimeMillis();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

    private List<String> extractStringArray(String json, String key) {
        List<String> values = new ArrayList<>();
        String marker = "\"" + key + "\":[";
        int idx = json.indexOf(marker);
        if (idx == -1) return values;

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

            values.add(json.substring(start, pos));
            pos++;
        }
        return values;
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
        return (long) extractDoubleValue(json, key);
    }

    private double extractDoubleValue(String json, String key) {
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
            return Double.parseDouble(json.substring(start, end));
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
