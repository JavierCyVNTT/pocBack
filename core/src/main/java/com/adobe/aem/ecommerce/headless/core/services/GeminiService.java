package com.adobe.aem.ecommerce.headless.core.services;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component(service = GeminiService.class, immediate = true)
@Designate(ocd = GeminiService.Config.class)
public class GeminiService {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiService.class);

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private String apiKey;

    @ObjectClassDefinition(name = "Gemini AI Service")
    public @interface Config {
        @AttributeDefinition(name = "Gemini API Key", type = AttributeType.STRING)
        String apiKey() default "";
    }

    @Activate
    @Modified
    protected void activate(Config config) {
        this.apiKey = config.apiKey();
    }

    /**
     * Sends a text-only prompt to Gemini and returns the generated text.
     */
    public String generateText(String prompt) throws Exception {
        String body = "{\"contents\":[{\"parts\":[{\"text\":" + jsonEscape(prompt) + "}]}]}";
        return callApi(body);
    }

    /**
     * Sends a prompt with inline images to Gemini (multimodal).
     * Each element in images is a String[2]: { base64data, mimeType }.
     */
    public String generateWithImages(String prompt, List<String[]> images) throws Exception {
        StringBuilder parts = new StringBuilder();
        parts.append("{\"text\":").append(jsonEscape(prompt)).append("}");
        for (String[] img : images) {
            parts.append(",{\"inline_data\":{\"mime_type\":\"")
                 .append(img[1])
                 .append("\",\"data\":\"")
                 .append(img[0])
                 .append("\"}}");
        }
        String body = "{\"contents\":[{\"parts\":[" + parts.toString() + "]}]}";
        return callApi(body);
    }

    private String callApi(String requestBody) throws Exception {
        int maxRetries = 2;
        int retryDelayMs = 5000;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            URL url = new URL(API_URL + "?key=" + apiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(requestBody);
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            if (status == 200) {
                return extractTextFromResponse(sb.toString());
            }

            if (status == 429 && attempt < maxRetries) {
                LOG.warn("Gemini rate limit (429), retrying in {}ms (attempt {}/{})", retryDelayMs, attempt + 1, maxRetries);
                Thread.sleep(retryDelayMs);
                retryDelayMs *= 2;
                continue;
            }

            LOG.error("Gemini API error {}: {}", status, sb.toString());
            throw new RuntimeException("Gemini API returned HTTP " + status + ": " + sb.toString());
        }

        throw new RuntimeException("Gemini API failed after retries");
    }

    /**
     * Extracts the text value from Gemini's response JSON:
     * candidates[0].content.parts[0].text
     */
    private String extractTextFromResponse(String json) {
        String marker = "\"text\":";
        int idx = json.indexOf(marker);
        if (idx == -1) return "";
        int start = json.indexOf('"', idx + marker.length()) + 1;
        if (start == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append('\\'); sb.append(next); break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String jsonEscape(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
