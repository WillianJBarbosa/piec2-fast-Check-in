package br.com.fastcheckin.api;

import br.com.fastcheckin.domain.hotel.Hotel;
import br.com.fastcheckin.domain.hotel.HotelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    public record HotelRequest(
            @NotBlank(message = "o nome do hotel é obrigatório")
            @Size(max = 150, message = "o nome do hotel deve ter no máximo 150 caracteres")
            String name
    ) {
    }

    public record HotelResponse(UUID id, String name, Instant createdAt) {

        static HotelResponse from(Hotel hotel) {
            return new HotelResponse(hotel.getId(), hotel.getName(), hotel.getCreatedAt());
        }
    }

    @PostMapping
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody HotelRequest request) {
        Hotel hotel = hotelService.register(request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(hotel.getId())
                .toUri();
        return ResponseEntity.created(location).body(HotelResponse.from(hotel));
    }

    @GetMapping
    public List<HotelResponse> list() {
        return hotelService.list().stream().map(HotelResponse::from).toList();
    }

    @GetMapping("/{id}")
    public HotelResponse get(@PathVariable UUID id) {
        return HotelResponse.from(hotelService.get(id));
    }

    @PutMapping("/{id}")
    public HotelResponse update(@PathVariable UUID id, @Valid @RequestBody HotelRequest request) {
        return HotelResponse.from(hotelService.rename(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
