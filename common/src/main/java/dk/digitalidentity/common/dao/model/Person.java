package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity
@Table(name = "persons")
@Setter
@Getter
public class Person {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@NotNull
	@Column
	private String uuid;

	@NotNull
	@Column
	private String cpr;

	@Size(max = 255)
	@Column
	private String name;

	@Size(max = 255)
	@Column
	private String email;

	@Enumerated(EnumType.STRING)
	@Column
	private NSISLevel nsisLevel;

	@Column
	private boolean nsisAllowed;
	
	@Column
	private boolean admin;

	@Column
	private boolean serviceProviderAdmin;
	
	@Column
	private boolean userAdmin;

	@Column
	private boolean registrant;
	
	@Column
	private boolean approvedConditions;

	@Column
	private LocalDateTime approvedConditionsTts;
	
	@Column
	private boolean lockedAdmin;

	@Column
	private boolean lockedPerson;
	
	@Column
	private boolean lockedDataset;

	@Column
	private boolean lockedPassword;

	@Column
	private boolean forceChangePassword;

	@Column
	private boolean lockedDead;

	@Column
	private LocalDateTime lockedPasswordUntil;

	@Column
	private long badPasswordCount;
	
	@Size(max = 255)
	@Column
	private String userId;

	@Size(max = 255)
	@Column
	private String nsisPassword;

	@Column
	private LocalDateTime nsisPasswordTimestamp;

	@Column
	private String samaccountName;

	@Column
	private String nemIdPid;

	@Column
	private String mitIdNameId;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@OneToOne(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
	private Supporter supporter;

	@Column
	private boolean nameProtected;

	@Size(max = 255)
	@Column
	private String nameAlias;
	
	@Column
	private long dailyPasswordChangeCounter;

	// TODO: kan en @BatchSize hjælpe her for at læse dem ud hurtigere?
	@ElementCollection
	@CollectionTable(name = "persons_attributes", joinColumns = { @JoinColumn(name = "person_id", referencedColumnName = "id") })
	@MapKeyColumn(name = "attribute_key")
	@Column(name = "attribute_value")
	private Map<String, String> attributes;

	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person")
	private List<PersonGroupMapping> groups;

	@BatchSize(size = 100)
	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "person", orphanRemoval = true)
	private List<KombitJfr> kombitJfrs;
	
	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CachedMfaClient> mfaClients;

	public boolean isOnlyLockedByPerson() {
		return lockedPerson && !lockedAdmin && !lockedDataset && !lockedPassword && !lockedDead;
	}

	public boolean isLocked() {
		return lockedPerson || lockedAdmin || lockedDataset || lockedPassword || lockedDead;
	}

	public String getIdentifier() {
		return getTopLevelDomain().getName().toLowerCase() + ":" + uuid.toLowerCase() + ":" + cpr + ":" + ((samaccountName != null) ? samaccountName.toLowerCase() : "<null>");
	}

	public boolean hasActivatedNSISUser() {
		// You have to have at least NSIS level LOW and you need to have approved conditions
		return isNsisAllowed() && isApprovedConditions() && NSISLevel.LOW.equalOrLesser(getNsisLevel());
	}

	public boolean isSupporter() {
		return supporter != null;
	}

	public void setSupporter(Supporter supporter) {
		if (supporter == null) {
			this.supporter = null;
			return;
		}

		supporter.setPerson(this);
		this.supporter = supporter;
	}
	
	@JsonIgnore
	public Domain getTopLevelDomain() {
		if (domain.getParent() != null) {
			return domain.getParent();
		}
		
		return domain;
	}
	
	@JsonIgnore
	public String getLowerSamAccountName() {
		if (samaccountName != null) {
			return samaccountName.toLowerCase();
		}
		
		return null;
	}
	
	@JsonIgnore
	public String getAzureId() {
		if (attributes == null || attributes.size() == 0) {
			return null;
		}
		
		return attributes.get("azureId");
	}
}
