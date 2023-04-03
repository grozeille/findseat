package fr.grozeille.findseat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "findseat")
@Data
public class FindSeatApplicationProperties {
    private String dataPath;
}
