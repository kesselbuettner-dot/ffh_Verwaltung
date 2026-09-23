package de.bierverein.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Arrays;

/** Only the worker generates tasks on its regular run; manager GET is a safe fallback. */
@Component
public class DeviceCycleTaskScheduler {
 private final DeviceCycleTaskService service;
 @Value("${spring.profiles.active:}") private String activeProfiles;
 public DeviceCycleTaskScheduler(DeviceCycleTaskService service){this.service=service;}
 @Scheduled(cron="0 10 6 * * *",zone="Europe/Berlin")
 public void daily(){
  if(Arrays.asList(activeProfiles.split(",")).contains("worker"))service.generateDue();
 }
}
