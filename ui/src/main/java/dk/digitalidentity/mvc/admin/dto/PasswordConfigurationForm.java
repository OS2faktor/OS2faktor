package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordConfigurationForm {
	private long domainId;
	private long minLength;
	private boolean requireLowercaseLetters;
	private boolean requireUppercaseLetters;
	private boolean requireComplexPassword;
	private boolean requireDigits;
	private boolean requireSpecialCharacters;
	private boolean forceChangePasswordEnabled;
	private boolean disallowDanishCharacters;
	private boolean disallowOldPasswords;
	private Long oldPasswordNumber;
	private Long forceChangePasswordInterval;
	private boolean replicateToAdEnabled;
	private boolean validateAgainstAdEnabled;
	private boolean monitoringEnabled;	
	private String monitoringEmail;
	private boolean disallowNameAndUsername;
	private boolean changePasswordOnUsersEnabled;
	private Long changePasswordOnUsersGroup;
	private boolean showAdSettings;
	private String alternativePasswordChangeLink;
	private Long triesBeforeLockNumber;
	private Long lockedMinutes;
	private boolean maxPasswordChangesPrDayEnabled;
	private Long maxPasswordChangesPrDay;
	private boolean canNotChangePasswordEnabled;
	private Long canNotChangePasswordGroup;

	public PasswordConfigurationForm(PasswordSetting settings) {
		this.minLength = settings.getMinLength();
		this.requireLowercaseLetters = settings.isRequireLowercaseLetters();
		this.requireUppercaseLetters = settings.isRequireUppercaseLetters();
		this.requireComplexPassword = settings.isRequireComplexPassword();
		this.requireDigits = settings.isRequireDigits();
		this.requireSpecialCharacters = settings.isRequireSpecialCharacters();
		this.forceChangePasswordEnabled = settings.isForceChangePasswordEnabled();
		this.forceChangePasswordInterval = settings.getForceChangePasswordInterval();
		this.disallowOldPasswords = settings.isDisallowOldPasswords();
		this.oldPasswordNumber = settings.getOldPasswordNumber();
		this.replicateToAdEnabled = settings.isReplicateToAdEnabled();
		this.validateAgainstAdEnabled = settings.isValidateAgainstAdEnabled();
		this.monitoringEnabled = settings.isMonitoringEnabled();
		this.monitoringEmail = settings.getMonitoringEmail();
		this.disallowDanishCharacters = settings.isDisallowDanishCharacters();
		this.disallowNameAndUsername = settings.isDisallowNameAndUsername();
		this.domainId = settings.getDomain().getId();
		this.changePasswordOnUsersEnabled = settings.isChangePasswordOnUsersEnabled();
		this.changePasswordOnUsersGroup = settings.getChangePasswordOnUsersGroup() != null ? settings.getChangePasswordOnUsersGroup().getId() : null;
		this.showAdSettings = true;
		this.alternativePasswordChangeLink = settings.getAlternativePasswordChangeLink();
		this.triesBeforeLockNumber = settings.getTriesBeforeLockNumber();
		this.lockedMinutes = settings.getLockedMinutes();
		this.maxPasswordChangesPrDayEnabled = settings.isMaxPasswordChangesPrDayEnabled();
		this.maxPasswordChangesPrDay = settings.getMaxPasswordChangesPrDay();
		this.canNotChangePasswordEnabled = settings.isCanNotChangePasswordEnabled();
		this.canNotChangePasswordGroup = settings.getCanNotChangePasswordGroup() != null ? settings.getCanNotChangePasswordGroup().getId() : null;
	}
}
