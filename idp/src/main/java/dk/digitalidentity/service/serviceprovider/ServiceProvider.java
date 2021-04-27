package dk.digitalidentity.service.serviceprovider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;

import org.apache.http.client.HttpClient;
import org.bouncycastle.util.encoders.Base64;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.security.credential.UsageType;
import org.springframework.beans.factory.annotation.Autowired;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import dk.digitalidentity.util.StringResource;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

public abstract class ServiceProvider {

    @Autowired
    protected HttpClient httpClient;

    public PublicKey getSigningKey() throws ResponderException, RequesterException {
        X509Certificate certificate = getX509Certificate(UsageType.SIGNING);
        if (certificate == null) {
        	throw new RequesterException("Kunne ikke finde tjenesteudbyderens 'X509Certificate' af typen SIGNING");
        }

        return certificate.getPublicKey();
    }

    public SingleLogoutService getLogoutEndpoint() throws ResponderException, RequesterException {
        // Any post endpoint
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);
        Optional<SingleLogoutService> match = ssoDescriptor
                .getSingleLogoutServices()
                .stream()
                .filter(singleLogoutService -> SAMLConstants.SAML2_POST_BINDING_URI.equals(singleLogoutService.getBinding()))
                .findFirst();

        // Any redirect endpoint
        if (match.isEmpty()) {
            match = ssoDescriptor
                    .getSingleLogoutServices()
                    .stream()
                    .filter(singleLogoutService -> SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(singleLogoutService.getBinding()))
                    .findFirst();
        }

        if (match.isEmpty()) {
            throw new ResponderException("Kunne ikke finde tjenesteudbyderens logout url i metadata");
        }

        return match.get();
    }

    public SingleLogoutService getLogoutResponseEndpoint() throws ResponderException, RequesterException {
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);

        Optional<SingleLogoutService> match = ssoDescriptor
                .getSingleLogoutServices()
                .stream()
                .filter(sloService -> SAMLConstants.SAML2_POST_BINDING_URI.equals(sloService.getBinding()))
                .findFirst();

        if (match.isEmpty()) {
            match = ssoDescriptor
                    .getSingleLogoutServices()
                    .stream()
                    .filter(sloService -> SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(sloService.getBinding()))
                    .findFirst();
        }

        if (match.isEmpty()) {
            throw new RuntimeException("Could not find Post or Redirect SLO Response endpoint in metadata");
        }

        return match.get();
    }

    public X509Certificate getX509Certificate(UsageType usageType) throws ResponderException, RequesterException {
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);

        // Find X509Cert in Metadata filtered by type
        Optional<KeyDescriptor> match = ssoDescriptor.getKeyDescriptors().stream()
                .filter(keyDescriptor -> keyDescriptor.getUse().equals(usageType)).findFirst();

        if (match.isEmpty()) {
        	return null;
        }

        org.opensaml.xmlsec.signature.X509Certificate x509Certificate = match.get().getKeyInfo().getX509Datas().get(0)
                .getX509Certificates().get(0);

        // Transform opensaml x509 cert --> java x509 cert
        byte[] bytes = Base64.decode(x509Certificate.getValue());
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        CertificateFactory instance = null;

        try {
            instance = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new ResponderException("Kunne ikke oprette 'X509 Certificate' læser", e);
        }

        try {
            return (X509Certificate) instance.generateCertificate(inputStream);
        } catch (CertificateException e) {
            throw new RequesterException("Kunne ikke læse X509 Certificate fra Metadata", e);
        }
    }

    protected HTTPMetadataResolver getMetadataResolver(String entityId, String metadataURL) throws ResponderException, RequesterException {
        try {
            HTTPMetadataResolver resolver = new HTTPMetadataResolver(httpClient, metadataURL);
            resolver.setId(entityId);
            resolver.setMinRefreshDelay(3 * 60 * 60 * 1000);
            resolver.setMaxRefreshDelay(3 * 60 * 60 * 1000);

            // Create paser pool for parsing metadata
            BasicParserPool parserPool = new BasicParserPool();
            resolver.setParserPool(parserPool);
            try {
                parserPool.initialize();
            }
            catch (ComponentInitializationException e) {
                throw new ResponderException("Kunne ikke initialisere HTTPMetadata læser", e);
            }

            // Initialize and save resolver for future use
            try {
                resolver.initialize();
            }
            catch (ComponentInitializationException e) {
                throw new RequesterException("Kunne ikke initialisere HTTPMetadata resolver", e);
            }

            return resolver;
        }
        catch (ResolverException e) {
            throw new ResponderException("Kunne ikke oprette MetadataResolver", e);
        }
    }

	protected AbstractReloadingMetadataResolver getMetadataResolver(String entityId, String metadataURL, String metadataContent) throws ResponderException, RequesterException {
		try {
			if (metadataURL != null && !metadataURL.isEmpty()) {
				return getMetadataResolver(entityId, metadataURL);
			} else if (metadataContent != null && !metadataContent.isEmpty()) {
				ResourceBackedMetadataResolver resolver = new ResourceBackedMetadataResolver(new StringResource(metadataContent, entityId));
				resolver.setId(entityId);

				// Create parser pool for parsing metadata
				BasicParserPool parserPool = new BasicParserPool();
				resolver.setParserPool(parserPool);
				try {
					parserPool.initialize();
				} catch (ComponentInitializationException e) {
					throw new ResponderException("Kunne ikke initialisere HTTPMetadata læser", e);
				}

				// Initialize and save resolver for future use
				try {
					resolver.initialize();
				} catch (ComponentInitializationException e) {
					throw new RequesterException("Kunne ikke initialisere HTTPMetadata resolver", e);
				}

				return resolver;
			} else {
				throw new ResponderException("Enten metadataURL eller metadataContent skal konfigureres.");
			}
		} catch (IOException e) {
			throw new ResponderException("Kunne ikke oprette MetadataResolver", e);
		}
	}

    public abstract EntityDescriptor getMetadata() throws RequesterException, ResponderException;
    public abstract String getNameId(Person person) throws ResponderException;
	public abstract String getNameIdFormat();
    public abstract Map<String, Object> getAttributes(Person person);
    public abstract boolean mfaRequired(AuthnRequest authnRequest);
    public abstract NSISLevel nsisLevelRequired(AuthnRequest authnRequest);
	public abstract boolean preferNemId();
    public abstract String getEntityId() throws RequesterException, ResponderException;
	public abstract String getName();
	public abstract boolean encryptAssertions();
	public abstract boolean enabled();
}
