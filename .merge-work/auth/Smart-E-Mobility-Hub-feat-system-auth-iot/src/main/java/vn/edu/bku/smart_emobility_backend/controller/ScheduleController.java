package vn.edu.bku.smart_emobility_backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.domain.ChargingSchedule;
import vn.edu.bku.smart_emobility_backend.common.domain.ScheduleProposal;
import vn.edu.bku.smart_emobility_backend.common.security.CurrentAccount;
import vn.edu.bku.smart_emobility_backend.dto.ScheduleRequests;
import vn.edu.bku.smart_emobility_backend.service.ChargingScheduler;

@RestController
@RequestMapping("/api/operator/charging-schedules")
public class ScheduleController {
    private final ChargingScheduler scheduler;

    public ScheduleController(ChargingScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping
    public ScheduleProposal build(@Valid @RequestBody ScheduleRequests.Build request,
            @AuthenticationPrincipal CurrentAccount account) {
        return scheduler.buildProposal(request.hubId(), account.accountId());
    }

    @GetMapping("/{id}")
    public ScheduleProposal find(@PathVariable String id, @AuthenticationPrincipal CurrentAccount account) {
        return scheduler.findProposal(id, account.accountId());
    }

    @PutMapping("/{id}/slots/{slotId}")
    public ScheduleProposal edit(@PathVariable String id, @PathVariable String slotId,
            @Valid @RequestBody ScheduleRequests.Edit request, @AuthenticationPrincipal CurrentAccount account) {
        return scheduler.editSlot(id, slotId, request.revision(), request.startAt(), request.endAt(), account.accountId());
    }

    @PostMapping("/{id}/confirm")
    public ChargingSchedule confirm(@PathVariable String id, @Valid @RequestBody ScheduleRequests.Confirm request,
            @AuthenticationPrincipal CurrentAccount account) {
        return scheduler.confirmSchedule(id, request.revision(), account.accountId());
    }
}
