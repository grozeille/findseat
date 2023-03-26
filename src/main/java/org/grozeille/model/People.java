package org.grozeille.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class People {
    public static final String DOMAIN = "worldcompany.com";
    private String firstname;

    private String lastname;

    private String email;

    public People(String firstname, String lastname) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = firstname+"."+lastname+ "@" + DOMAIN;
    }
}
