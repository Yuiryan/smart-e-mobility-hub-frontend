package vn.edu.bku.smart_emobility_backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.CurrentAccount;
import vn.edu.bku.smart_emobility_backend.simulation.Scenario;
import vn.edu.bku.smart_emobility_backend.simulation.SimulationEngine;
import vn.edu.bku.smart_emobility_backend.simulation.SimulationResult;

@RestController
@RequestMapping("/api/operator/simulations")
public class SimulationController {
    private final SimulationEngine engine;

    public SimulationController(SimulationEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    public SimulationResult run(@Valid @RequestBody Scenario scenario, @AuthenticationPrincipal CurrentAccount account) {
        return engine.runSimulation(scenario, account.accountId());
    }
}
