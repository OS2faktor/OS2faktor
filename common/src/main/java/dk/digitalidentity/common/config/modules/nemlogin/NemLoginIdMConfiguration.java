package dk.digitalidentity.common.config.modules.nemlogin;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginIdMConfiguration {
	
	@FeatureDocumentation(name = "NemLog-in IdM", description = "Integration til NemLog-in IdM (MitID Erhverv)")
	private boolean enabled = false;
	
	private String keystoreLocation;
	private String keystorePassword;
	private String invoiceMethodUuid;
	private String defaultEmail;
	private boolean disableSendingRid = false;

	private String baseUrl = "https://services.nemlog-in.dk";
	
	// enable ONCE and only ONCE
	private boolean migrateExistingUsers = false;
}
