package fr.grozeille.findseat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {
    private int week;

    private BookingType monday;
    private BookingType tuesday;
    private BookingType wednesday;
    private BookingType thursday;
    private BookingType friday;

    private boolean confirmed;
}
