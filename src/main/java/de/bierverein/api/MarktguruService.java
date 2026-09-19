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

    /**
     * Parses the live Marktguru /api/v1/offers/search response.
     * The relevant offer fields are directly under results[] as observed in
     * the live response: advertisers[], product{}, unit{}, price,
     * oldPrice, referencePrice, volume, quantity and validityDates[].
     */
    // Package-private for deterministic unit tests using captured Marktguru responses.
    List<Offer> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode results = root.path("results");
        if (!results.isArray()) return List.of();

        List<Offer> offers = new ArrayList<>();
        for (JsonNode n : results) {
            BigDecimal price = decimal(n, "price");
            if (price == null) continue;

            List<Retailer> retailers = parseRetailers(n.path("advertisers"));
            Retailer primaryRetailer = retailers.isEmpty() ? null : retailers.get(0);

            JsonNode product = n.path("product");
            JsonNode unit = n.path("unit");
            List<Validity> validityDates = parseValidityDates(n.path("validityDates"));
            Validity primaryValidity = validityDates.isEmpty() ? new Validity(null, null) : validityDates.get(0);

            offers.add(new Offer(
                    text(n, "id"),
                    primaryRetailer == null ? null : primaryRetailer.name(),
                    primaryRetailer == null ? null : primaryRetailer.uniqueName(),
                    retailers,
                    text(product, "name"),
                    text(n, "description"),
                    price,
                    decimal(n, "oldPrice"),
                    decimal(n, "referencePrice"),
                    decimal(n, "volume"),
                    decimal(n, "quantity"),
                    bool(n, "isMultiProduct"),
                    text(unit, "name"),
                    text(unit, "shortName"),
                    validityDates,
                    primaryValidity.from(),
                    primaryValidity.to(),
                    bool(n, "requiresLoyalityMembership"),
                    text(n, "externalUrl"),
                    text(n, "leafletFlightId")
            ));
        }
        return offers;
    }

    private static List<Retailer> parseRetailers(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<Retailer> result = new ArrayList<>();
        for (JsonNode r : node) {
            String name = text(r, "name");
            if (name != null && !name.isBlank()) {
                result.add(new Retailer(name, text(r, "uniqueName"), text(r, "id")));
            }
        }
        return List.copyOf(result);
    }

    private static List<Validity> parseValidityDates(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<Validity> result = new ArrayList<>();
        for (JsonNode v : node) {
            String from = text(v, "from");
            String to = text(v, "to");
            if (from != null || to != null) result.add(new Validity(from, to));
        }
        return List.copyOf(result);
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

    /** Canonicalizes units used for package comparison. */
    static String normalizeUnit(String unit) {
        if (unit == null) return null;
        String u = unit.trim().toLowerCase(Locale.ROOT).replace(".", "");
        return switch (u) {
            case "l", "liter", "litre", "ltr" -> "l";
            case "ml", "milliliter", "millilitre" -> "ml";
            case "cl", "centiliter", "centilitre" -> "cl";
            case "dl", "deciliter", "decilitre", "deziliter", "dezilitre" -> "dl";
            case "kg", "kilogram", "kilograms" -> "kg";
            case "g", "gram", "grams" -> "g";
            default -> u;
        };
    }

    static BigDecimal normalizedVolume(BigDecimal value, String unit) {
        if (value == null || unit == null) return null;
        return switch (normalizeUnit(unit)) {
            case "l" -> value;
            case "ml" -> value.divide(new BigDecimal("1000"));
            case "cl" -> value.divide(new BigDecimal("100"));
            case "dl" -> value.divide(new BigDecimal("10"));
            default -> null;
        };
    }

    /**
     * Compares package quantity and per-item volume independent of ml/l/cl/dl
     * notation and harmless decimal rounding differences. Unknown/missing
     * package data never counts as a match.
     */
    /**
     * Compares the individual article size only. The purchasing package count
     * is intentionally ignored because Marktguru is searched by article name
     * plus single-unit size, not by the number of pieces in a case.
     */
    static boolean sameSize(BigDecimal v1, String u1, BigDecimal v2, String u2) {
        if (v1 == null || u1 == null || v2 == null || u2 == null) return false;
        String n1 = normalizeUnit(u1), n2 = normalizeUnit(u2);
        BigDecimal nv1 = normalizedVolume(v1, n1), nv2 = normalizedVolume(v2, n2);
        if (nv1 != null && nv2 != null) {
            return sameDecimal(nv1, nv2, new BigDecimal("0.000001"));
        }
        return n1.equals(n2) && sameDecimal(v1, v2, new BigDecimal("0.000001"));
    }

    static boolean samePackage(BigDecimal q1, BigDecimal v1, String u1,
                               BigDecimal q2, BigDecimal v2, String u2) {
        if (q1 == null || v1 == null || u1 == null || q2 == null || v2 == null || u2 == null) return false;
        if (!sameDecimal(q1, q2, new BigDecimal("0.001"))) return false;
        String n1 = normalizeUnit(u1), n2 = normalizeUnit(u2);
        BigDecimal nv1 = normalizedVolume(v1, n1), nv2 = normalizedVolume(v2, n2);
        if (nv1 != null && nv2 != null) {
            return sameDecimal(nv1, nv2, new BigDecimal("0.000001"));
        }
        return n1.equals(n2) && sameDecimal(v1, v2, new BigDecimal("0.000001"));
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    public record Retailer(String name, String uniqueName, String id) {}

    public record Validity(String from, String to) {}

    public record Offer(
            String offerId,
            String retailer,
            String retailerKey,
            List<Retailer> retailers,
            String productName,
            String description,
            BigDecimal price,
            BigDecimal oldPrice,
            BigDecimal referencePrice,
            BigDecimal volume,
            BigDecimal quantity,
            boolean multiProduct,
            String unitName,
            String unitShortName,
            List<Validity> validityDates,
            String validFrom,
            String validTo,
            boolean loyaltyRequired,
            String externalUrl,
            String leafletFlightId) {}
}
