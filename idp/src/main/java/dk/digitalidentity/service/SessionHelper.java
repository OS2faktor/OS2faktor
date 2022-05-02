package dk.digitalidentity.service;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.joda.time.DateTime;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.impl.AuthnRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.AuthnRequestUnmarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestMarshaller;
import org.opensaml.saml.saml2.core.impl.LogoutRequestUnmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Element;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SessionHelper {

	@Autowired
	private PersonService personService;

	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;

	@Autowired
	private SessionSettingService sessionService;
	
	@Autowired
	private AuditLogger auditLogger;

	private SecretKeySpec secretKey;
	
	public void saveIncomingAuthnRequest(AuthnRequest authnRequest, String relayState) throws ResponderException {
		setAuthnRequest(authnRequest);
		setRelayState(relayState);
	}

	public NSISLevel getLoginState() {
		Person person = getPerson();
		if (person == null) {
			return null;
		}

		NSISLevel passwordLevel = getPasswordLevel();
		NSISLevel mfaLevel = getMFALevel();
		NSISLevel personLevel = person.getNsisLevel();

		log.debug("passwordLevel = " + passwordLevel);
		log.debug("mfaLevel = " + mfaLevel);
		log.debug("personLevel = " + personLevel);

		if (NSISLevel.HIGH.equalOrLesser(personLevel) && NSISLevel.HIGH.equalOrLesser(passwordLevel) && NSISLevel.HIGH.equalOrLesser(mfaLevel)) {
			log.debug("LoginState evaluated to HIGH");
			return NSISLevel.HIGH;
		}

		if (NSISLevel.SUBSTANTIAL.equalOrLesser(personLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(passwordLevel) && NSISLevel.SUBSTANTIAL.equalOrLesser(mfaLevel)) {
			log.debug("LoginState evaluated to SUBSTANTIAL");
			return NSISLevel.SUBSTANTIAL;
		}

		// Does not need any mfa to verify for low
		if (NSISLevel.LOW.equalOrLesser(personLevel) && NSISLevel.LOW.equalOrLesser(passwordLevel)) {
			log.debug("LoginState evaluated to LOW");
			return NSISLevel.LOW;
		}

		if (NSISLevel.NONE.equalOrLesser(personLevel) && NSISLevel.NONE.equalOrLesser(passwordLevel)) {
			log.debug("LoginState evaluated to NONE");
			return NSISLevel.NONE;
		}

		log.debug("LoginState evaluated to null");
		return null;
	}

	public void clearSession() {
		if (log.isDebugEnabled()) {
			log.debug("Clearing session");
		}

		setPasswordLevel(null);
		setMFALevel(null);
		setPerson(null);
		setMFAClients(null);
		setServiceProviderSessions(null);
		setAuthenticatedWithADPassword(false);
		setPasswordChangeSuccessRedirect(null);
		setPasswordChangeFailureReason(null);
		setInDedicatedActivateAccountFlow(false);
	}

	public NSISLevel getPasswordLevel() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL);
		LocalDateTime timestamp = getPasswordLevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			Long passwordExpiry = sessionService.getSettings(person.getDomain()).getPasswordExpiry();
			if (LocalDateTime.now().minusMinutes(passwordExpiry).isAfter(timestamp)) {
				auditLogger.sessionExpired(person);
				setPasswordLevelTimestamp(null);
				setPasswordLevel(null);
				return null;
			}

			return (NSISLevel) attribute;
		}

		return null;
	}

	// Will only elevate permissions or delete them
	public void setPasswordLevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (nsisLevel == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL, null);
			setPasswordLevelTimestamp(null);
			return;
		}

		NSISLevel passwordLevel = getPasswordLevel();
		if (passwordLevel == null || !passwordLevel.isGreater(nsisLevel)) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL, nsisLevel);
			setPasswordLevelTimestamp(LocalDateTime.now());
		}

		if (log.isDebugEnabled()) {
			log.debug("SetPasswordLevel: was=" + (passwordLevel != null ? passwordLevel : "<null>") + " now=" + nsisLevel);
		}
	}

	public LocalDateTime getPasswordLevelTimestamp() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object timestamp = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setPasswordLevelTimestamp(LocalDateTime timestamp) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_AUTHENTIFICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public NSISLevel getMFALevel() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL);
		LocalDateTime timestamp = getMFALevelTimestamp();
		Person person = getPerson();

		if (attribute != null && timestamp != null && person != null) {
			Long mfaExpiry = sessionService.getSettings(person.getDomain()).getMfaExpiry();
			if (LocalDateTime.now().minusMinutes(mfaExpiry).isAfter(timestamp)) {
				setMFALevelTimestamp(null);
				setMFALevel(null);
				return null;
			}

			return (NSISLevel) attribute;
		}

		return null;
	}

	// Will only elevate permissions or delete them
	public void setMFALevel(NSISLevel nsisLevel) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (nsisLevel == null) {
			httpServletRequest.getSession().removeAttribute(Constants.MFA_AUTHENTICATION_LEVEL);
			setMFALevelTimestamp(null);
			return;
		}

		NSISLevel mfaLevel = getMFALevel();
		if (mfaLevel == null || !mfaLevel.isGreater(nsisLevel)) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL, nsisLevel);
			LocalDateTime now = LocalDateTime.now();
			setMFALevelTimestamp(now);
			setPasswordLevelTimestamp(now);
		}

		if (log.isDebugEnabled()) {
			log.debug("SetMFALevel: was=" + (mfaLevel != null ? mfaLevel : "<null>") + " now=" + nsisLevel);
		}
	}

	public LocalDateTime getMFALevelTimestamp() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object timestamp = httpServletRequest.getSession().getAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP);
		if (timestamp != null) {
			return (LocalDateTime) timestamp;
		}

		return null;
	}

	private void setMFALevelTimestamp(LocalDateTime timestamp) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (timestamp == null) {
			httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, null);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_AUTHENTICATION_LEVEL_TIMESTAMP, timestamp);
	}

	public LogoutRequest getLogoutRequest() throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.LOGOUT_REQUEST);

		if (attribute == null) {
			return null;
		}

		try {
			Element marshalledLogoutRequest = (Element) attribute;
			return (LogoutRequest) new LogoutRequestUnmarshaller().unmarshall(marshalledLogoutRequest);
		} catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode logout forespørgsel (LogoutRequest)", ex);
		}
	}

	public void setLogoutRequest(LogoutRequest logoutRequest) throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (logoutRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.LOGOUT_REQUEST, null);
			return;
		}

		try {
			Element marshall = new LogoutRequestMarshaller().marshall(logoutRequest);
			httpServletRequest.getSession().setAttribute(Constants.LOGOUT_REQUEST, marshall);
		} catch (MarshallingException ex) {
			throw new ResponderException("Kunne ikke omforme logout forespørgsel (LogoutRequest)", ex);
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Map<String, String>> getServiceProviderSessions() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.SERVICE_PROVIDER);
		if (attribute != null) {
			return (Map<String, Map<String, String>>) attribute;
		} else {
			return new HashMap<>();
		}
	}

	public void setServiceProviderSessions(Map<String, Map<String, String>> serviceProviders) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.SERVICE_PROVIDER, serviceProviders);
	}


	public boolean handleValidateIP() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return true;
		}

		// Get current IP
		String remoteAddr = httpServletRequest.getHeader("X-FORWARDED-FOR");
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = httpServletRequest.getRemoteAddr();
		}

		// Get stored IP
		Object attribute = httpServletRequest.getSession().getAttribute(Constants.IP_ADDRESS);

		// Validate IP against one stored on session
		if (attribute == null) {
			// If nothing stored on session save remoteAddr on session
			httpServletRequest.getSession().setAttribute(Constants.IP_ADDRESS, remoteAddr);
			return true;
		}
		else {
			if (Objects.equals((String) attribute, remoteAddr)) {
				return true;
			}
			else {
				auditLogger.logoutCausedByIPChange(getPerson());

				// IP on session and from current request is not the same, so force the user to reauthenticate
				setPerson(null);
				setMFALevel(null);
				setPasswordLevel(null);
				
				httpServletRequest.getSession().setAttribute(Constants.IP_ADDRESS, remoteAddr);
				
				return false;
			}
		}
	}

	public AuthnRequest getAuthnRequest() throws ResponderException {		
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		HttpSession session = httpServletRequest.getSession();
		Object attribute = session.getAttribute(Constants.AUTHN_REQUEST);
		if (attribute == null) {
			return null;
		}

		try {
			Element marshalledAuthnRequest = (Element) attribute;
			return (AuthnRequest) new AuthnRequestUnmarshaller().unmarshall(marshalledAuthnRequest);
		}
		catch (UnmarshallingException ex) {
			throw new ResponderException("Kunne ikke afkode login forespørgsel, Fejl url ikke kendt", ex);
		}
	}

	public void setAuthnRequest(AuthnRequest authnRequest) throws ResponderException {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (authnRequest == null) {
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, null);
			return;
		}

		try {
			Element marshall = new AuthnRequestMarshaller().marshall(authnRequest);
			httpServletRequest.getSession().setAttribute(Constants.AUTHN_REQUEST, marshall);
		}
		catch (MarshallingException ex) {
			throw new ResponderException("Kunne ikke omforme login forespørgsel (AuthnRequest)", ex);
		}
	}

	public String getRelayState() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.RELAY_STATE);
	}

	public void setRelayState(String relayState) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.RELAY_STATE, relayState);
	}

	public DateTime getAuthnInstant() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHN_INSTANT);
		return (attribute != null ? (DateTime) attribute : null);
	}

	public DateTime setAuthnInstant(DateTime dateTime) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		httpServletRequest.getSession().setAttribute(Constants.AUTHN_INSTANT, dateTime);
		return null;
	}

	public Person getPerson() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}

		return null;
	}

	public void setPerson(Person person) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<MfaClient> getMFAClients() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_CLIENTS);
		if (attribute != null) {
			try {
				if (attribute instanceof MfaClient) {
					return Collections.singletonList((MfaClient) attribute);
				}

				return (List<MfaClient>) attribute;
			}
			catch (Exception ex) {
				log.error("Could not cast what was stored in the session as a List<MfaClient>", ex);

				Person person = getPerson();
				log.warn("Class: " + attribute.getClass() + ", Person on session:" + (person != null ? person.getUuid() : "<null>"));
			}
		}
		return null;
	}

	public void setMFAClients(List<MfaClient> mfaDevices) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_CLIENTS, mfaDevices);
	}

	public MfaClient getSelectedMFAClient() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.MFA_SELECTED_CLIENT);
		if (attribute != null) {
			return (MfaClient) attribute;
		}
		return null;
	}

	public void setSelectedMFAClient(MfaClient mfaClient) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MFA_SELECTED_CLIENT, mfaClient);
	}

	public String getSubscriptionKey() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.SUBSCRIPTION_KEY);
	}

	public void setSubscriptionKey(String subscriptionKey) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.SUBSCRIPTION_KEY, subscriptionKey);
	}

	public boolean isAuthenticatedWithADPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithADPassword(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_AD_PASSWORD, b);
	}
	
	public boolean isDoNotUseCurrentADPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DO_NOT_USE_CURRENT_AD_PASSWORD);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setDoNotUseCurrentADPassword(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			log.warn("Unable to find servletRequest in setDoNotUseCurrentADPassword = " + b);
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DO_NOT_USE_CURRENT_AD_PASSWORD, b);
	}

	public boolean isAuthenticatedWithNemIdOrMitId() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AUTHENTICATED_WITH_NEMID_OR_MITID);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setAuthenticatedWithNemIdOrMitId(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AUTHENTICATED_WITH_NEMID_OR_MITID, b);
	}

	public String getPassword() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		String encryptedPassword = (String) httpServletRequest.getSession().getAttribute(Constants.PASSWORD);
		return decryptString(encryptedPassword);
	}

	public void setPassword(String password) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		if (password == null) {
			httpServletRequest.getSession().setAttribute(Constants.PASSWORD, null);
			return;
		}
		httpServletRequest.getSession().setAttribute(Constants.PASSWORD, encryptString(password));
	}

	private SecretKeySpec getKey(String myKey) {
		if (secretKey != null) {
			return secretKey;
		}

		byte[] key;
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			log.error("Error in generating key", e);
		}

		return secretKey;
	}

	private String decryptString(String encryptedString) {
		if (!StringUtils.hasLength(encryptedString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
			cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
			return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedString)));
		} catch (Exception e) {
			log.error("Error while decrypting string", e);
		}
		return null;
	}

	private String encryptString(String rawString) {
		if (!StringUtils.hasLength(rawString)) {
			return null;
		}

		try {
			SecretKeySpec key = getKey(os2faktorConfiguration.getPassword().getSecret());
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00});
			cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
			return Base64.getEncoder().encodeToString(cipher.doFinal(rawString.getBytes("UTF-8")));
		} catch (Exception e) {
			log.error("Error while encrypting string", e);
			throw new RuntimeException(e);
		}
	}

	public String getNemIDPid() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.NEMID_PID);
	}

	public void setNemIDPid(String pid) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.NEMID_PID, pid);
	}

	public String getMitIDNameID() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		return (String) httpServletRequest.getSession().getAttribute(Constants.MIT_ID_NAME_ID);
	}

	public void setMitIDNameID(String nameId) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.MIT_ID_NAME_ID, nameId);
	}

	public Person getADPerson() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.AD_PERSON_ID);

		if (attribute != null) {
			long personId = (long) attribute;
			return personService.getById(personId);
		}
		return null;
	}

	public void setADPerson(Person person) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.AD_PERSON_ID, person == null ? null : person.getId());
	}

	@SuppressWarnings("unchecked")
	public List<Person> getAvailablePeople() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		List<Long> attribute = (List<Long>) httpServletRequest.getSession().getAttribute(Constants.AVAILABLE_PEOPLE);
		return attribute.stream().map(l -> personService.getById(l)).collect(Collectors.toList());
	}

	public void setAvailablePeople(List<Person> people) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		List<Long> peopleIds = people.stream().map(Person::getId).collect(Collectors.toList());
		httpServletRequest.getSession().setAttribute(Constants.AVAILABLE_PEOPLE, peopleIds);
	}

	public boolean isInDedicatedActivateAccountFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DEDICATED_ACTIVATE_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInDedicatedActivateAccountFlow(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DEDICATED_ACTIVATE_ACCOUNT_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InDedicatedActivateAccountFlow: " + b);
		}
	}
	
	public boolean isInChangePasswordFlowAndHasNotApprovedConditions() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInChangePasswordFlowAndHasNotApprovedConditions(Boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_NOT_APPROVED_CONDITIONS, b);

		if (log.isDebugEnabled()) {
			log.debug("InChangePasswordFlowAndHasNotApprovedConditions: " + b);
		}
	}

	public boolean isInInsufficientNSISLevelFromMitIDFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInInsufficientNSISLevelFromMitIDFlow(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.INSUFFICIENT_NSIS_LEVEL_FROM_MIT_ID, b);

		if (log.isDebugEnabled()) {
			log.debug("InDedicatedActivateAccountFlow: " + b);
		}
	}

	public void setInActivateAccountFlow(boolean b) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.ACTIVATE_ACCOUNT_FLOW, b);

		if (log.isDebugEnabled()) {
			log.debug("InActivateAccountFlow: " + b);
		}
	}

	public boolean isInActivateAccountFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.ACTIVATE_ACCOUNT_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInApproveConditionsFlow(boolean inApproveConditionsFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.APPROVE_CONDITIONS_FLOW, inApproveConditionsFlow);

		if (log.isDebugEnabled()) {
			log.debug("InApproveConditionsFlow: " + inApproveConditionsFlow);
		}
	}

	public boolean isInApproveConditionsFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.APPROVE_CONDITIONS_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInPasswordChangeFlow(boolean inPasswordChangeFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_FLOW, inPasswordChangeFlow);

		if (log.isDebugEnabled()) {
			log.debug("InPasswordChangeFlow: " + inPasswordChangeFlow);
		}
	}

	public boolean isInPasswordChangeFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInPasswordExpiryFlow(boolean inPasswordExpiryFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_EXPIRY_FLOW, inPasswordExpiryFlow);

		if (log.isDebugEnabled()) {
			log.debug("InPasswordExpiryFlow: " + inPasswordExpiryFlow);
		}
	}

	public boolean isInPasswordExpiryFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_EXPIRY_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setInForceChangePasswordFlow(boolean inForceChangePasswordFlow) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_FORCE_CHANGE_FLOW, inForceChangePasswordFlow);

		if (log.isDebugEnabled()) {
			log.debug("InForceChangePasswordFlow: " + inForceChangePasswordFlow);
		}
	}

	public boolean isInForceChangePasswordFlow() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_FORCE_CHANGE_FLOW);
		return (boolean) (attribute != null ? attribute : false);
	}

	public void setDeclineUserActivation(boolean declineUserActivation) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.DECLINE_USER_ACTIVATION, declineUserActivation);

		if (log.isDebugEnabled()) {
			log.debug("DeclineUserActivation: " + declineUserActivation);
		}
	}

	public boolean isDeclineUserActivation() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return false;
		}

		Object attribute = httpServletRequest.getSession().getAttribute(Constants.DECLINE_USER_ACTIVATION);
		return (boolean) (attribute != null ? attribute : false);
	}

	public String getPasswordChangeSuccessRedirect() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object redirectUrl = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT);
		if (redirectUrl == null) {
			return null;
		}

		return (String) redirectUrl;
	}

	public void setPasswordChangeSuccessRedirect(String redirectUrl) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_SUCCESS_REDIRECT, redirectUrl);
	}
	
	public ChangePasswordResult getPasswordChangeFailureReason() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return null;
		}

		Object passwordChangeFailureReason = httpServletRequest.getSession().getAttribute(Constants.PASSWORD_CHANGE_FAILURE_REASON);
		if (passwordChangeFailureReason == null) {
			return null;
		}

		return (ChangePasswordResult) passwordChangeFailureReason;
	}

	public void setPasswordChangeFailureReason(ChangePasswordResult passwordChangeFailureReason) {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().setAttribute(Constants.PASSWORD_CHANGE_FAILURE_REASON, passwordChangeFailureReason);
	}

	public void clearFlowStates() {
		setInPasswordChangeFlow(false);
		setInPasswordExpiryFlow(false);
		setInActivateAccountFlow(false);
		setInApproveConditionsFlow(false);
		setInForceChangePasswordFlow(false);
		setInDedicatedActivateAccountFlow(false);
		setInInsufficientNSISLevelFromMitIDFlow(false);
		setInChangePasswordFlowAndHasNotApprovedConditions(false);
	}

	public void logout(LogoutRequest logoutRequest) throws ResponderException {
		// Delete everything not needed for logout procedure
		// We need Person and ServiceProviderSessions
		setAuthnRequest(null);

		// Password
		setPasswordLevel(null);
		setPasswordLevelTimestamp(null);
		setPassword(null);

		// MFA
		setMFALevel(null);
		setMFALevelTimestamp(null);
		setMFAClients(null);
		setSelectedMFAClient(null);
		setSubscriptionKey(null);

		// Other
		setAuthenticatedWithADPassword(false);
		setAuthenticatedWithNemIdOrMitId(false);
		setADPerson(null);
		setNemIDPid(null);
		setAvailablePeople(new ArrayList<>()); // This does not handle null case
		setInActivateAccountFlow(false);
		setInPasswordChangeFlow(false);
		setInPasswordExpiryFlow(false);
		setInApproveConditionsFlow(false);

		// Save LogoutRequest to session if one is provided
		if (logoutRequest != null) {
			setLogoutRequest(logoutRequest);
		}

		if (log.isDebugEnabled()) {
			log.debug("Session logout");
		}
	}

	public void invalidateSession() {
		HttpServletRequest httpServletRequest = getServletRequest();
		if (httpServletRequest == null) {
			return;
		}

		httpServletRequest.getSession().invalidate();

		if (log.isDebugEnabled()) {
			log.debug("Session invalidated");
		}
	}

	private HttpServletRequest getServletRequest() {
		RequestAttributes attribs = RequestContextHolder.getRequestAttributes();

		if (attribs instanceof ServletRequestAttributes) {
			return ((ServletRequestAttributes) attribs).getRequest();
		}

		return null;
	}

	public String serializeSessionAsString() {
		StringBuilder sb = new StringBuilder();

		HttpServletRequest servletRequest = getServletRequest();
		if (servletRequest == null) {
			// Pretty sure this never happens, but im going to keep the null check
			return "No HttpServletRequest found";
		}

		HttpSession session = servletRequest.getSession(false);
		if (session == null) {
			return "No Session associated with request";
		}

		for (Enumeration<String> attributeNames = session.getAttributeNames(); attributeNames.hasMoreElements(); ) {
			String attributeName = attributeNames.nextElement();
			Object attribute = session.getAttribute(attributeName);

			// TODO: I really want a white-liste instead - and for all others we just dump non/not-null

			// Specific cases
			List<String> doNotPrint = List.of(
					Constants.PASSWORD,
					"org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN",
					"dk.os2faktor.nemid.challenge",
					"SPRING_SECURITY_SAVED_REQUEST"
			);

			if (doNotPrint.contains(attributeName)) {
				continue;
			}

			// Generic handling
			sb.append(attributeName).append(": ");
			if (attribute == null) {
				sb.append("<null>");
			}
			else if (attribute instanceof String) {
				sb.append((String) attribute);
			}
			else if (attribute instanceof Boolean) {
				sb.append(((Boolean) attribute));
			}
			else if (attribute instanceof NSISLevel) {
				sb.append(((NSISLevel) attribute));
			}
			else if (attribute instanceof DateTime) {
				sb.append(((DateTime) attribute));
			}
			else if (attribute instanceof LocalDateTime) {
				sb.append(((LocalDateTime) attribute));
			}
			else {
				sb.append("<not-null>");
			}
			sb.append("\n");
		}

		return sb.toString();
	}
}
