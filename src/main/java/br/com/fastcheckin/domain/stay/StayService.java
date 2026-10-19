package br.com.fastcheckin.domain.stay;

import br.com.fastcheckin.domain.hotel.Hotel;
import br.com.fastcheckin.domain.hotel.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
                .orElseThrow(() -> new IllegalArgumentException("Hotel não encontrado."));
        return stayRepository.save(new Stay(hotel, arrivalDate, departureDate));
    }
}
