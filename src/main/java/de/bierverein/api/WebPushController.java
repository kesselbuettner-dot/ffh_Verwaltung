package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class WebPushController {
 private final WebPushService push; public WebPushController(WebPushService p){push=p;}
 @GetMapping("/public-key") public Map<String,Object> key(){return Map.of("enabled",push.enabled(),"publicKey",push.publicKey());}
 @PostMapping("/subscribe") @ResponseStatus(HttpStatus.NO_CONTENT) public void subscribe(@RequestBody Request r,Authentication a){validate(r);push.subscribe(a.getName(),r.endpoint(),r.keys().p256dh(),r.keys().auth());}
 @PostMapping("/unsubscribe") @ResponseStatus(HttpStatus.NO_CONTENT) public void unsubscribe(@RequestBody Request r,Authentication a){if(r==null||r.endpoint()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);push.unsubscribe(a.getName(),r.endpoint());}
 private void validate(Request r){if(r==null||r.endpoint()==null||r.endpoint().isBlank()||r.keys()==null||r.keys().p256dh()==null||r.keys().auth()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiges Push-Abonnement");}
 public record Keys(String p256dh,String auth){} public record Request(String endpoint,Keys keys){}
}
