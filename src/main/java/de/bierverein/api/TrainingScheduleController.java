package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/training/events")
public class TrainingScheduleController {
    private final TrainingScheduleService service;
    public TrainingScheduleController(TrainingScheduleService service){this.service=service;}

    @GetMapping public TrainingScheduleService.ModuleView list(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,Authentication auth){return service.list(auth.getName(),from,to);}
    @GetMapping("/dashboard") public java.util.List<TrainingScheduleService.OccurrenceView> dashboard(Authentication auth){return service.dashboard(auth.getName());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public TrainingScheduleService.EventView create(@RequestBody TrainingScheduleService.EventRequest request,Authentication auth){return service.create(auth.getName(),request);}
    @PutMapping("/{id}") public TrainingScheduleService.EventView update(@PathVariable Long id,@RequestBody TrainingScheduleService.EventRequest request,Authentication auth){return service.update(auth.getName(),id,request);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Authentication auth){service.delete(auth.getName(),id);}
    @PostMapping("/{id}/response") public TrainingScheduleService.OccurrenceView respond(@PathVariable Long id,@RequestBody ResponseRequest request,Authentication auth){return service.respond(auth.getName(),id,request.occurrenceDate(),request.status());}
    public record ResponseRequest(LocalDate occurrenceDate,String status){}
}
