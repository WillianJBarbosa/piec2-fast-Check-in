package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.stay.Stay;
import br.com.fastcheckin.domain.stay.StayService;
import br.com.fastcheckin.domain.stay.StayStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stays")
public class StayController {

    private final StayService stayService;

    public StayController(StayService stayService) {
        this.stayService = stayService;
    }

    public record StayRequest(
            @NotNull(message = "o hotel é obrigatório") UUID hotelId,
            @NotNull(message = "a data de chegada é obrigatória") LocalDate arrivalDate,
            @NotNull(message = "a data de saída é obrigatória") LocalDate departureDate
    ) {
    }

    public record StayUpdateRequest(
            @NotNull(message = "a data de chegada é obrigatória") LocalDate arrivalDate,
            @NotNull(message = "a data de saída é obrigatória") LocalDate departureDate,
            @NotNull(message = "o status é obrigatório") StayStatus status
    ) {
    }

    public record StayResponse(
            UUID id,
            UUID hotelId,
            LocalDate arrivalDate,
            LocalDate departureDate,
            StayStatus status,
            Instant createdAt
    ) {

        static StayResponse from(Stay stay) {
            return new StayResponse(
                    stay.getId(),
                    stay.getHotel().getId(),
                    stay.getArrivalDate(),
                    stay.getDepartureDate(),
                    stay.getStatus(),
                    stay.getCreatedAt()
            );
        }
    }

    @PostMapping
    public ResponseEntity<StayResponse> create(@Valid @RequestBody StayRequest request) {
        Stay stay = stayService.schedule(request.hotelId(), request.arrivalDate(), request.departureDate());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(stay.getId())
                .toUri();
        return ResponseEntity.created(location).body(StayResponse.from(stay));
    }

    @GetMapping
    public List<StayResponse> list(@RequestParam(required = false) UUID hotelId) {
        return stayService.list(hotelId).stream().map(StayResponse::from).toList();
    }

    @GetMapping("/{id}")
    public StayResponse get(@PathVariable UUID id) {
        return StayResponse.from(stayService.get(id));
    }

    @PutMapping("/{id}")
    public StayResponse update(@PathVariable UUID id, @Valid @RequestBody StayUpdateRequest request) {
        return StayResponse.from(
                stayService.update(id, request.arrivalDate(), request.departureDate(), request.status())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        stayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
