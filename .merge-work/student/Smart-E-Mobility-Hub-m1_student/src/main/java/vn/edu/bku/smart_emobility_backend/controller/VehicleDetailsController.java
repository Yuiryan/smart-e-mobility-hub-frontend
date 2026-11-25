package vn.edu.bku.smart_emobility_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.dto.VehicleDetails;
import vn.edu.bku.smart_emobility_backend.service.VehicleLookupService;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleDetailsController {
    private final VehicleLookupService vehicleLookupService;

    public VehicleDetailsController(VehicleLookupService vehicleLookupService) {
        this.vehicleLookupService = vehicleLookupService;
    }

    @GetMapping("/{vehicleId}")
    public VehicleDetails getVehicleDetails(@PathVariable String vehicleId) {
        return vehicleLookupService.getVehicleDetails(vehicleId);
    }


}
