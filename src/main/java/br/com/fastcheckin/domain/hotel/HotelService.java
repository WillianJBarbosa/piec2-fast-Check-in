package br.com.fastcheckin.domain.hotel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Transactional
    public Hotel register(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do hotel é obrigatório.");
        }
        return hotelRepository.save(new Hotel(name.trim()));
    }
}
