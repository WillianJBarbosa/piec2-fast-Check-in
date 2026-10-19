CREATE TABLE hotels (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_hotels_name UNIQUE (name)
);

CREATE TABLE guests (
    id UUID PRIMARY KEY,
    personal_data_ciphertext TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE stays (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL,
    arrival_date DATE NOT NULL,
    departure_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_stays_hotel FOREIGN KEY (hotel_id) REFERENCES hotels (id),
    CONSTRAINT ck_stays_dates CHECK (departure_date > arrival_date)
);

CREATE INDEX ix_stays_hotel_id ON stays (hotel_id);

CREATE TABLE fnrh_registrations (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL,
    guest_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_fnrh_stay FOREIGN KEY (stay_id) REFERENCES stays (id),
    CONSTRAINT fk_fnrh_guest FOREIGN KEY (guest_id) REFERENCES guests (id),
    CONSTRAINT uq_fnrh_stay_guest UNIQUE (stay_id, guest_id)
);

CREATE INDEX ix_fnrh_guest_id ON fnrh_registrations (guest_id);
