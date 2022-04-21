package dk.digitalidentity.dev;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.DomainService;

@Controller
@Component
public class DevBootstrapper {
	private SecureRandom random = new SecureRandom();

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private DomainService domainService;

	@Autowired
	private Flyway flyway;
	
	@PostConstruct
	public void init() {
		if (configuration.getDev().isEnabled()) {
			if (personDao.findAll().size() == 0) {
				Domain domain = domainService.getInternalDomain();

				Person person = new Person();
				person.setUuid("54dfff62-b5ff-49d8-a1bd-e1e256043f5b");
				person.setAdmin(true);
				person.setCpr("0806500178");
				person.setEmail("someuser@kommune.dk");
				person.setName("Test User Ellie");
				person.setNsisLevel(NSISLevel.NONE);
				person.setNsisAllowed(true);
				person.setSamaccountName("ellie999");
				person.setDomain(domain);

				person = personDao.save(person);

				List<Person> persons = new ArrayList<>();
				for (int i = 0; i < 5000; i++) {
					person = new Person();
					person.setUuid(UUID.randomUUID().toString());
					person.setCpr(randomCpr());
					person.setName(randomName());
					person.setNsisLevel(NSISLevel.NONE);
					person.setNsisAllowed(random.nextBoolean());
					person.setSamaccountName(randomUserId());
					person.setDomain(domain);
					
					persons.add(person);
				}
				
				personDao.saveAll(persons);
			}
		}
	}

	private String getInt(int min, int max) {
		int val = random.nextInt(max + 1 - min) + min;
		int width = (int) Math.log10(max);

		String result = "" + val;
		while (result.length() < width) {
			result = "0" + result;
		}
		
		return result;
	}
	
	private String randomCpr() {
		return getInt(1, 28) + getInt(1, 12) + getInt(1,99) + getInt(0,9999);
	}
	
	private String randomName() {
		String result = "";
		switch (random.nextInt(4)) {
			case 0:
				result = "Jane ";
				break;
			case 1:
				result = "John ";
				break;
			case 2:
				result = "Jackson ";
				break;
			case 3:
				result = "Julie ";
				break;
		}
		
		switch (random.nextInt(4)) {
			case 0:
				result += "Petersen ";
				break;
			case 1:
				result += "Jensen ";
				break;
			case 2:
				result += "Hansen ";
				break;
			case 3:
				result += "Svendsen ";
				break;
		}

		return result + getInt(1, 9999);
	}
	
	private String randomUserId() {
		return "user" + getInt(1, 999999);
	}
	
	// TODO This is part of the nonsecured pages, should probably have "test" apiKey on endpoint
	@GetMapping("/bootstrap/db/clean")
	public ResponseEntity<?> cleanDB() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		flyway.clean();
		flyway.migrate();
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/bootstrap/users/init")
	public ResponseEntity<?> initUsers() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		init();
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/bootstrap/users/setPassword")
	public ResponseEntity<?> setPassword() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisLevel(NSISLevel.SUBSTANTIAL);
			person.setNsisPassword(encoder.encode("Test123456"));
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setApprovedConditions")
	public ResponseEntity<?> setApprovedConditions() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setApprovedConditions(true);
			person.setApprovedConditionsTts(LocalDateTime.now());
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setNSISAllowed")
	public ResponseEntity<?> setNSISAllowed() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisAllowed(true);
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/bootstrap/users/setAdPassword")
	public ResponseEntity<?> setADPassword() {
		if (!configuration.getDev().isEnabled()) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		List<Person> all = personDao.findAll();
		for (Person person : all) {
			person.setNsisLevel(NSISLevel.NONE);
		}

		personDao.saveAll(all);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
