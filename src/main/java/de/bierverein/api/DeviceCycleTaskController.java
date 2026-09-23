package de.bierverein.api;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** No broad /api/devices permission is needed by assigned members: access is per task. */
@RestController
@RequestMapping("/api/my-tasks/device-inspections")
public class DeviceCycleTaskController {
 private final DeviceCycleTaskService service;
 public DeviceCycleTaskController(DeviceCycleTaskService service){this.service=service;}
 @GetMapping
 public List<DeviceCycleTaskService.TaskView> list(@RequestParam(defaultValue="false") boolean includeDone,
     Authentication auth){return service.list(auth,includeDone);}
 @GetMapping("/assignees")
 public List<DeviceCycleTaskService.PersonView> eligible(Authentication auth){return service.members(auth);}
 @PutMapping("/{id}/assignee")
 public DeviceCycleTaskService.TaskView assign(@PathVariable Long id,
    @RequestBody DeviceCycleTaskService.AssignInput input,Authentication auth){
  return service.assign(id,input,auth);
 }
 @PostMapping("/{id}/complete")
 public DeviceCycleTaskService.TaskView complete(@PathVariable Long id,
    @RequestBody DeviceCycleTaskService.FinishInput input,Authentication auth){
  return service.finish(id,input,auth);
 }
}
