package dk.digitalidentity.rest.registration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.datatables.PersonPasswordChangeDatatableDao;
import dk.digitalidentity.datatables.PersonRegistrationDatatableDao;
import dk.digitalidentity.datatables.model.PersonPasswordChangeView;
import dk.digitalidentity.datatables.model.RegistrationPersonView;
import dk.digitalidentity.security.RequireRegistrant;
import jakarta.validation.Valid;

@RequireRegistrant
@RestController
public class RegistrationRestController {

	@Autowired
	private PersonRegistrationDatatableDao personRegistrationDatatableDao;

	@Autowired
	private PersonPasswordChangeDatatableDao personPasswordChangeDatatableDao;

	@PostMapping("/rest/registration/persons")
	public DataTablesOutput<RegistrationPersonView> registerDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<RegistrationPersonView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return personRegistrationDatatableDao.findAll(input);
	}

	@PostMapping("/rest/registration/changepassword/persons")
	public DataTablesOutput<PersonPasswordChangeView> changePasswordDataTable(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<PersonPasswordChangeView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return personPasswordChangeDatatableDao.findAll(input);
	}
}