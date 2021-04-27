package dk.digitalidentity.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.security.access.prepost.PreAuthorize;

@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ROLE_SUPPORTER') or hasRole('ROLE_REGISTRANT')")
public @interface RequireAnyAdminRole {

}
