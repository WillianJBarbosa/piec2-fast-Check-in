package br.com.fastcheckin.domain.hotel;

import br.com.fastcheckin.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        return hotelRepository.saveAndFlush(new Hotel(name.trim()));
    }

    @Transactional(readOnly = true)
    public List<Hotel> list() {
        return hotelRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Hotel get(UUID id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel não encontrado."));
    }

    @Transactional
    public Hotel rename(UUID id, String newName) {
        Hotel hotel = get(id);
        hotel.rename(newName);
        hotelRepository.flush();
        return hotel;
    }

    @Transactional
    public void delete(UUID id) {
        Hotel hotel = get(id);
        hotelRepository.delete(hotel);
        hotelRepository.flush();
    }
}
