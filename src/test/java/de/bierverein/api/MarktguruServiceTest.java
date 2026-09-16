package de.bierverein.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarktguruServiceTest {
    private MarktguruService service;

    @BeforeEach
    void setUp() {
        service = new MarktguruService(
                new ObjectMapper(),
                "https://api.marktguru.de/api/v1/offers/search",
                "test-api-key",
                "test-client-key",
                "04109",
                24);
    }

    @Test
    void parsesLidlOfferWithReferencePriceAndMultipack() throws Exception {
        var offers = service.parse(LIVE_RESPONSE);

        assertEquals(4, offers.size());

        var lidlMultipack = offers.stream()
                .filter(o -> "Lidl".equals(o.retailer()))
                .filter(o -> "12".equals(o.quantity().stripTrailingZeros().toPlainString()))
                .findFirst()
                .orElseThrow();

        assertEquals(new BigDecimal("6.66"), lidlMultipack.price());
        assertEquals(new BigDecimal("1.68"), lidlMultipack.referencePrice());
        assertEquals(new BigDecimal("0.33"), lidlMultipack.volume());
        assertEquals("Liter", lidlMultipack.unitName());
        assertEquals("l", lidlMultipack.unitShortName());
        assertTrue(lidlMultipack.multiProduct());
        assertEquals("2026-09-16T22:00:00Z", lidlMultipack.validFrom());
        assertEquals("2026-09-19T21:59:00Z", lidlMultipack.validTo());
        assertEquals(1, lidlMultipack.validityDates().size());
    }

    @Test
    void parsesPennyOfferAndLidlRetailers() throws Exception {
        var offers = service.parse(LIVE_RESPONSE);

        var retailers = offers.stream().map(MarktguruService.Offer::retailer).toList();
        assertTrue(retailers.contains("Lidl"));
        assertTrue(retailers.contains("PENNY"));
        assertTrue(retailers.contains("Netto Marken-Discount"));

        var penny = offers.stream()
                .filter(o -> "PENNY".equals(o.retailer()))
                .findFirst()
                .orElseThrow();

        assertEquals(new BigDecimal("1.19"), penny.price());
        assertEquals(new BigDecimal("0.95"), penny.referencePrice());
        assertEquals(new BigDecimal("1.25"), penny.volume());
        assertEquals(new BigDecimal("1.00"), penny.quantity());
        assertEquals("Limonade", penny.productName());
        assertEquals("retailers/126765", penny.retailers().get(0).id());
    }

    @Test
    void parsesMultipleValidityDates() throws Exception {
        String json = """
                {"results":[{
                  "id":999,
                  "price":2.49,
                  "referencePrice":1.99,
                  "advertisers":[{"id":"retailers/126679","name":"Lidl","uniqueName":"lidl"}],
                  "product":{"id":1,"name":"Testartikel"},
                  "quantity":2,
                  "volume":0.5,
                  "isMultiProduct":true,
                  "unit":{"id":1,"name":"Liter","shortName":"l"},
                  "validityDates":[
                    {"from":"2026-09-16T22:00:00Z","to":"2026-09-19T21:59:00Z"},
                    {"from":"2026-09-23T22:00:00Z","to":"2026-09-26T21:59:00Z"}
                  ]
                }]} 
                """;

        var offer = service.parse(json).get(0);

        assertEquals(2, offer.validityDates().size());
        assertEquals("2026-09-16T22:00:00Z", offer.validityDates().get(0).from());
        assertEquals("2026-09-19T21:59:00Z", offer.validityDates().get(0).to());
        assertEquals("2026-09-23T22:00:00Z", offer.validityDates().get(1).from());
        assertEquals("2026-09-26T21:59:00Z", offer.validityDates().get(1).to());
        assertEquals("2026-09-16T22:00:00Z", offer.validFrom());
        assertEquals("2026-09-19T21:59:00Z", offer.validTo());
    }

    @Test
    void ignoresResultsWithoutPrice() throws Exception {
        String json = """
                {"results":[
                  {"id":1,"price":null,"advertisers":[{"name":"Lidl"}]},
                  {"id":2,"price":1.99,"advertisers":[{"name":"PENNY"}]}
                ]}
                """;

        var offers = service.parse(json);
        assertEquals(1, offers.size());
        assertEquals("PENNY", offers.get(0).retailer());
    }

    // Captured response from the user's live API test (PLZ 04109, q=Coca-Cola).
    private static final String LIVE_RESPONSE = """
        {
          "filters":{"retailers":[{"id":126679,"name":"Lidl","resultsCount":2},{"id":126735,"name":"Netto Marken-Discount","resultsCount":1},{"id":126765,"name":"PENNY","resultsCount":1}],"brands":[{"id":104494,"name":"Coca-Cola","resultsCount":4}],"categories":[{"id":359,"name":"Softdrinks","resultsCount":4}]},
          "totalResults":4,"skippedResults":0,
          "results":[
            {"brand":{"uniqueName":"coca-cola","id":104494,"name":"Coca-Cola"},"advertisers":[{"uniqueName":"lidl","id":"retailers/126679","name":"Lidl"}],"id":24894553,"description":"HINWEIS: MIT LIDL PLUS APP 0.99 € FANTA/ MEZZO MIX/SPRITE Versch. Sorten. Teilweise koffeeinhaltig. Je 1,25 l zzgl. 0.25 Pfand","volume":1.25,"quantity":1.00,"isMultiProduct":true,"price":1.19,"oldPrice":null,"referencePrice":0.95,"requiresLoyalityMembership":false,"validityDates":[{"from":"2026-09-20T22:00:00Z","to":"2026-09-26T21:59:00Z"}],"product":{"id":351468,"name":"Cola","description":"~twxus~ ~twxnam~"},"unit":{"shortName":"l","id":1,"name":"Liter"}},
            {"brand":{"uniqueName":"coca-cola","id":104494,"name":"Coca-Cola"},"advertisers":[{"uniqueName":"netto-marken-discount","id":"retailers/126735","name":"Netto Marken-Discount"}],"id":24843858,"description":"oder Fanta teilweise koffeinhaltig, versch. Sorten 6 x 0,33 Liter zzgl. Pfand 1.50","volume":0.33,"quantity":6.00,"isMultiProduct":true,"price":3.99,"oldPrice":null,"referencePrice":2.02,"requiresLoyalityMembership":false,"validityDates":[{"from":"2026-09-13T22:00:00Z","to":"2026-09-19T21:59:00Z"}],"product":{"id":351468,"name":"Cola","description":"~twxus~ ~twxnam~"},"unit":{"shortName":"l","id":1,"name":"Liter"}},
            {"brand":{"uniqueName":"coca-cola","id":104494,"name":"Coca-Cola"},"advertisers":[{"uniqueName":"lidl","id":"retailers/126679","name":"Lidl"}],"id":24782493,"description":"Versch. Sorten. Koffein haltig. Je 12 x 0,33 l zzgl. 3.00 Pfand Standardpackung: 6x 0,33 l","volume":0.33,"quantity":12.00,"isMultiProduct":true,"price":6.66,"oldPrice":null,"referencePrice":1.68,"requiresLoyalityMembership":false,"validityDates":[{"from":"2026-09-16T22:00:00Z","to":"2026-09-19T21:59:00Z"}],"product":{"id":351468,"name":"Cola","description":"~twxus~ ~twxnam~"},"unit":{"shortName":"l","id":1,"name":"Liter"}},
            {"brand":{"uniqueName":"coca-cola","id":104494,"name":"Coca-Cola"},"advertisers":[{"uniqueName":"penny","id":"retailers/126765","name":"PENNY"}],"id":24817818,"description":"HINWEIS: MIT PENNY APP 1.11 € Fanta oder Sprite Erfrischungsgetränk, versch. Sorten, tw. koffeinhaltig, zzgl. 0.25 Pfand, je 1,25 l","volume":1.25,"quantity":1.00,"isMultiProduct":true,"price":1.19,"oldPrice":null,"referencePrice":0.95,"requiresLoyalityMembership":false,"validityDates":[{"from":"2026-09-13T22:00:00Z","to":"2026-09-19T21:59:00Z"}],"product":{"id":1144791,"name":"Limonade","description":""},"unit":{"shortName":"l","id":1,"name":"Liter"}}
          ]
        }
        """;
}
