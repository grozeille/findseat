package fr.grozeille.findseat;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Duration;

@SpringBootApplication
public class FindseatApplication {

	public static void main(String[] args) {
		SpringApplication.run(FindseatApplication.class, args);
	}

}
