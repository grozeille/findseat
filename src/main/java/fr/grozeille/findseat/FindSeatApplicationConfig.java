package fr.grozeille.findseat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FindSeatApplicationConfig {
    @Bean
    public FindSeatApplicationProperties findSeatApplicationProperties() {
        return new FindSeatApplicationProperties();
    }
}
