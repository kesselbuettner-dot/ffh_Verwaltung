package de.bierverein.api;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private static final DateTimeFormatter ICAL_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final DashboardMessageRepository messages;
    private final CalendarSubscriptionRepository subscriptions;

    public CalendarController(DashboardMessageRepository messages, CalendarSubscriptionRepository subscriptions) {
        this.messages = messages;
        this.subscriptions = subscriptions;
    }

    @GetMapping(value = "/appointments.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> download(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return calendarResponse(true);
    }

    @PostMapping("/subscription")
    @Transactional
    public SubscriptionView subscription(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        CalendarSubscription subscription = subscriptions.findByUsername(authentication.getName()).orElseGet(() -> {
            CalendarSubscription created = new CalendarSubscription();
            created.setUsername(authentication.getName());
            created.setToken(newToken());
            return subscriptions.save(created);
        });
        return new SubscriptionView("/api/calendar/feed/" + subscription.getToken() + ".ics");
    }

    @PostMapping("/subscription/regenerate")
    @Transactional
    public SubscriptionView regenerate(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        CalendarSubscription subscription = subscriptions.findByUsername(authentication.getName()).orElseGet(() -> {
            CalendarSubscription created = new CalendarSubscription();
            created.setUsername(authentication.getName());
            return created;
        });
        subscription.setToken(newToken());
        subscriptions.save(subscription);
        return new SubscriptionView("/api/calendar/feed/" + subscription.getToken() + ".ics");
    }

    @GetMapping(value = "/feed/{token}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> feed(@PathVariable String token) {
        if (token == null || token.length() < 24 || subscriptions.findByToken(token).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return calendarResponse(false);
    }

    private ResponseEntity<byte[]> calendarResponse(boolean attachment) {
        byte[] body = buildCalendar().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"));
        headers.setCacheControl(CacheControl.noCache());
        if (attachment) headers.setContentDisposition(ContentDisposition.attachment().filename("ffh-termine.ics").build());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private String buildCalendar() {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//FFH Verwaltung//Termine//DE");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:FFH Termine");
        messages.findAllByOrderByPriorityDescEventAtAscCreatedAtDesc().stream()
                .filter(message -> message.isActive()
                        && "APPOINTMENT".equals(message.getType())
                        && message.getEventAt() != null)
                .forEach(message -> {
                    line(out, "BEGIN:VEVENT");
                    line(out, "UID:ffh-appointment-" + message.getId() + "@ffh-verwaltung");
                    line(out, "DTSTAMP:" + ICAL_TIME.format(message.getCreatedAt()));
                    line(out, "DTSTART:" + ICAL_TIME.format(message.getEventAt()));
                    line(out, "DTEND:" + ICAL_TIME.format(message.getEventAt().plus(Duration.ofHours(1))));
                    line(out, "SUMMARY:" + escape(message.getTitle()));
                    if (message.getBody() != null) line(out, "DESCRIPTION:" + escape(message.getBody()));
                    line(out, "STATUS:CONFIRMED");
                    line(out, "END:VEVENT");
                });
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private void line(StringBuilder out, String value) {
        if (value.isEmpty()) {
            out.append("\r\n");
            return;
        }
        boolean first = true;
        String remaining = value;
        while (!remaining.isEmpty()) {
            int end = utf8Cut(remaining, first ? 75 : 74);
            if (!first) out.append(' ');
            out.append(remaining, 0, end).append("\r\n");
            remaining = remaining.substring(end);
            first = false;
        }
    }

    private int utf8Cut(String value, int maxBytes) {
        int index = 0;
        int bytes = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + codePointBytes > maxBytes && index > 0) break;
            bytes += codePointBytes;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
                .replace(",", "\\,").replace(";", "\\;");
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    public record SubscriptionView(String feedPath) {}
}
