package org.grozeille.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class People implements Cloneable {
    public static final String DOMAIN = "worldcompany.com";
    private String firstname;

    private String lastname;

    private String email;

    @NonNull
    private ReservationType reservationType;

    public People(String firstname, String lastname) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = firstname+"."+lastname+ "@" + DOMAIN;
        this.reservationType = ReservationType.NORMAL;
    }

    public Object clone() {
        return new People(this.firstname, this.lastname, this.email, this.reservationType);
    }
}
