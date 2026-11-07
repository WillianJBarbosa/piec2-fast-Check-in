package br.com.fastcheckin.domain.stay;

import br.com.fastcheckin.common.ResourceNotFoundException;
import br.com.fastcheckin.domain.hotel.Hotel;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class StayService {

    private final StayRepository stayRepository;
    private final HotelRepository hotelRepository;

    public StayService(StayRepository stayRepository, HotelRepository hotelRepository) {
        this.stayRepository = stayRepository;
        this.hotelRepository = hotelRepository;
    }

    @Transactional
    public Stay schedule(UUID hotelId, LocalDate arrivalDate, LocalDate departureDate) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel não encontrado."));
        return stayRepository.save(new Stay(hotel, arrivalDate, departureDate));
    }

    @Transactional(readOnly = true)
    public List<Stay> list(UUID hotelId) {
        return hotelId == null ? stayRepository.findAll() : stayRepository.findByHotelId(hotelId);
    }

    @Transactional(readOnly = true)
    public Stay get(UUID id) {
        return stayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estadia não encontrada."));
    }

    @Transactional
    public Stay update(UUID id, LocalDate arrivalDate, LocalDate departureDate, StayStatus status) {
        Stay stay = get(id);
        stay.reschedule(arrivalDate, departureDate);
        stay.changeStatus(status);
        stayRepository.flush();
        return stay;
    }

    @Transactional
    public void delete(UUID id) {
        Stay stay = get(id);
        stayRepository.delete(stay);
        stayRepository.flush();
    }
}
