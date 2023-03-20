package org.grozeille;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class Team {
    @NonNull
    private String name;
    @NonNull
    private Integer size; // TODO temporary because it's easier to test by forcing the size, but should be calculated based on the members
    @NonNull // Used only for Lombok
    private boolean mandatory;
    private List<People> members = new ArrayList<>();
    @NonNull
    private String managerEmail;
}
