package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditlogs")
@Setter
@Getter
public class AuditLog {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column
	private LocalDateTime tts;
		
	@Column
	private String ipAddress;
	
	@Column
	private String location;
	
	@Column
	private String correlationId;

	// referenced person which data was used

	// has to be EAGER because of IGNORE below
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "person_id")
	@JsonIgnore
	@NotFound(action = NotFoundAction.IGNORE)
	private Person person;
	
	@Column
	private String personName;

	@Column
	private String personDomain;
	
	@Column
	private String cpr;
	
	// referenced entity that performed the action (null if the performer was the data-owner)

	@Column
	private Long performerId;
	
	@Column
	private String performerName;
	
	// action performed

	@Column
	@Enumerated(EnumType.STRING)
	private LogAction logAction;

	@Column
	private String message;
	
	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	@JoinColumn(name = "auditlogs_details_id")
	@JsonIgnore
	private AuditLogDetail details;
}
