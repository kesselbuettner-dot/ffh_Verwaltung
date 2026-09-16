package de.bierverein.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MarktguruService {
    private final RestClient http;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String apiKey;
    private final String clientKey;
    private final String zipCode;
    private final int limit;

    public MarktguruService(ObjectMapper mapper,
                            @Value("${app.marktguru.endpoint:https://api.marktguru.de/api/v1/offers/search}") String endpoint,
                            @Value("${app.marktguru.api-key:}") String apiKey,
                            @Value("${app.marktguru.client-key:}") String clientKey,
                            @Value("${app.marktguru.zip:04109}") String zipCode,
                            @Value("${app.marktguru.limit:24}") int limit) {
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.clientKey = clientKey;
        this.zipCode = zipCode;
        this.limit = Math.max(1, Math.min(limit, 100));
        this.http = RestClient.builder().build();
    }

    public boolean configured() {
        return !endpoint.isBlank() && !apiKey.isBlank() && !clientKey.isBlank();
    }

    public List<Offer> search(String query) {
        if (!configured() || query == null || query.isBlank()) return List.of();
        try {
            String url = endpoint + (endpoint.contains("?") ? "&" : "?")
                    + "as=web"
                    + "&limit=" + limit
                    + "&offset=0"
                    + "&q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&zipCode=" + URLEncoder.encode(zipCode, StandardCharsets.UTF_8);

            String body = http.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("x-clientkey", clientKey)
                    .header("x-apikey", apiKey)
                    .retrieve()
                    .body(String.class);

            return parse(body);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Offer> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode results = root.path("results");
        if (!results.isArray()) return List.of();

        List<Offer> offers = new ArrayList<>();
        for (JsonNode n : results) {
            BigDecimal price = decimal(n, "price");
            if (price == null) continue;

            JsonNode advertiser = n.path("advertisers").isArray() && n.path("advertisers").size() > 0
                    ? n.path("advertisers").get(0) : null;
            JsonNode product = n.path("product");
            JsonNode unit = n.path("unit");
            JsonNode validity = n.path("validityDates").isArray() && n.path("validityDates").size() > 0
                    ? n.path("validityDates").get(0) : null;

            offers.add(new Offer(
                    text(n, "id"),
                    text(advertiser, "name"),
                    text(advertiser, "uniqueName"),
                    text(product, "name"),
                    text(n, "description"),
                    price,
                    decimal(n, "oldPrice"),
                    decimal(n, "referencePrice"),
                    text(unit, "name"),
                    text(unit, "shortName"),
                    text(validity, "from"),
                    text(validity, "to"),
                    bool(n, "requiresLoyalityMembership"),
                    text(n, "externalUrl"),
                    text(n, "leafletFlightId")
            ));
        }
        return offers;
    }

    private static String text(JsonNode n, String key) {
        return n == null || n.isMissingNode() || n.isNull() || !n.hasNonNull(key) ? null : n.get(key).asText();
    }

    private static BigDecimal decimal(JsonNode n, String key) {
        String value = text(n, key);
        if (value == null) return null;
        try { return new BigDecimal(value.replace(',', '.')); }
        catch (Exception ignored) { return null; }
    }

    private static boolean bool(JsonNode n, String key) {
        return n != null && n.has(key) && n.get(key).asBoolean(false);
    }

    public record Offer(String offerId, String retailer, String retailerKey, String productName,
                        String description, BigDecimal price, BigDecimal oldPrice,
                        BigDecimal referencePrice, String unitName, String unitShortName,
                        String validFrom, String validTo, boolean loyaltyRequired,
                        String externalUrl, String leafletFlightId) {}
}
