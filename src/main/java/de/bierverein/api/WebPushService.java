package de.bierverein.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.util.*;

@Service
public class WebPushService {
 private final PushSubscriptionRepository subscriptions; private final ObjectMapper json;
 @Value("${VAPID_PUBLIC_KEY:}") private String publicKey;
 @Value("${VAPID_PRIVATE_KEY:}") private String privateKey;
 @Value("${VAPID_SUBJECT:mailto:admin@example.org}") private String subject;
 public WebPushService(PushSubscriptionRepository s,ObjectMapper j){subscriptions=s;json=j;if(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)==null)Security.addProvider(new BouncyCastleProvider());}
 public boolean enabled(){return publicKey!=null&&!publicKey.isBlank()&&privateKey!=null&&!privateKey.isBlank();}
 public String publicKey(){return publicKey==null?"":publicKey;}
 @Transactional public void subscribe(String username,String endpoint,String p256dh,String auth){PushSubscription s=subscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);s.setEndpoint(endpoint);s.setP256dh(p256dh);s.setAuth(auth);s.setUsername(username);subscriptions.save(s);}
 @Transactional public void unsubscribe(String username,String endpoint){subscriptions.deleteByEndpointAndUsername(endpoint,username);}
 public void sendMessage(DashboardMessage message){if(!enabled()||!message.isActive())return;Map<String,Object> payload=Map.of("title",message.getTitle(),"body",Optional.ofNullable(message.getBody()).orElse("Eine neue Meldung ist verfügbar."),"url","/?messages=1","tag","ffh-message-"+message.getId());List<Long> invalid=new ArrayList<>();for(PushSubscription s:subscriptions.findAll()){try{PushService service=new PushService(publicKey,privateKey,subject);Notification notification=new Notification(s.getEndpoint(),s.getP256dh(),s.getAuth(),json.writeValueAsBytes(payload));var response=service.send(notification);if(response.getStatusLine().getStatusCode()==404||response.getStatusLine().getStatusCode()==410)invalid.add(s.getId());}catch(Exception ignored){}}if(!invalid.isEmpty())subscriptions.deleteAllById(invalid);}
}
