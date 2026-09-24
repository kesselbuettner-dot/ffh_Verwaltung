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
 /** Only the named, subscribed member receives the reminder. No document, ID or
     medical details are ever included in the push payload. */
 public boolean sendToUser(String username,String title,String body,String link,String tag) {
  if(!enabled()||username==null||username.isBlank())return false;
  Map<String,Object> payload=Map.of("title",title,"body",body,"url",link,"tag",tag);
  boolean delivered=false;
  List<Long> invalid=new ArrayList<>();
  for(PushSubscription subscription:subscriptions.findByUsername(username)){
   try{
    PushService service=new PushService(publicKey,privateKey,subject);
    Notification notice=new Notification(subscription.getEndpoint(),subscription.getP256dh(),
      subscription.getAuth(),json.writeValueAsBytes(payload));
    var response=service.send(notice);
    int status=response.getStatusLine().getStatusCode();
    if(status>=200&&status<300)delivered=true;
    if(status==404||status==410)invalid.add(subscription.getId());
   }catch(Exception ignored){/* No push body or subscription secrets in logs. */}
  }
  if(!invalid.isEmpty())subscriptions.deleteAllById(invalid);
  return delivered;
 }
 /** A new dated appointment uses the existing Web Push subscriptions. */
 public void sendAppointment(DashboardMessage appointment){
  if(!enabled()||!appointment.isActive()||appointment.getEventAt()==null)return;
  Map<String,Object> payload=Map.of("title","Neuer Termin: "+appointment.getTitle(),
   "body",java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(java.time.ZoneId.of("Europe/Berlin")).format(appointment.getEventAt()),
   "url","/?appointments=1","tag","ffh-appointment-"+appointment.getId());
  List<Long> invalid=new ArrayList<>();
  for(PushSubscription subscription:subscriptions.findAll()){
   try{
    PushService service=new PushService(publicKey,privateKey,subject);
    Notification notice=new Notification(subscription.getEndpoint(),subscription.getP256dh(),subscription.getAuth(),json.writeValueAsBytes(payload));
    int code=service.send(notice).getStatusLine().getStatusCode();
    if(code==404||code==410)invalid.add(subscription.getId());
   }catch(Exception ignored){}
  }
  if(!invalid.isEmpty())subscriptions.deleteAllById(invalid);
 }
 public void sendMessage(DashboardMessage message){if(!enabled()||!message.isActive())return;Map<String,Object> payload=Map.of("title",message.getTitle(),"body",Optional.ofNullable(message.getBody()).orElse("Eine neue Meldung ist verfügbar."),"url","/?messages=1","tag","ffh-message-"+message.getId());List<Long> invalid=new ArrayList<>();for(PushSubscription s:subscriptions.findAll()){try{PushService service=new PushService(publicKey,privateKey,subject);Notification notification=new Notification(s.getEndpoint(),s.getP256dh(),s.getAuth(),json.writeValueAsBytes(payload));var response=service.send(notification);if(response.getStatusLine().getStatusCode()==404||response.getStatusLine().getStatusCode()==410)invalid.add(s.getId());}catch(Exception ignored){}}if(!invalid.isEmpty())subscriptions.deleteAllById(invalid);}
}
